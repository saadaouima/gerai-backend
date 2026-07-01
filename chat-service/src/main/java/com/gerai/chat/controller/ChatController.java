package com.gerai.chat.controller;

import com.gerai.chat.config.WebSocketAuthChannelInterceptor;
import com.gerai.chat.dto.*;
import com.gerai.chat.repository.ConversationParticipantRepository;
import com.gerai.chat.service.ChatService;
import com.gerai.chat.service.FileStorageService;
import com.gerai.chat.service.KeycloakAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur principal de messagerie du microservice chat-service.
 * <p>
 * {@code @RestController} : combine {@code @Controller} et {@code @ResponseBody},
 * toutes les méthodes retournent directement du JSON.
 * <br>
 * {@code @RequestMapping("/api/chat")} : préfixe commun à tous les endpoints REST de ce contrôleur.
 * <br>
 * {@code @RequiredArgsConstructor} (Lombok) : génère l'injection par constructeur de toutes les dépendances finales.
 * <br>
 * {@code @CrossOrigin} : autorise les requêtes CORS depuis le frontend Angular (configurable via propriété).
 * <p>
 * Ce contrôleur gère :
 * <ul>
 *   <li>Les conversations (liste, création directe, création de groupe).</li>
 *   <li>Les messages REST (lecture, envoi, marquage lu, upload de fichiers).</li>
 *   <li>Les destinations STOMP WebSocket ({@code @MessageMapping}) pour la messagerie temps réel.</li>
 *   <li>Des endpoints alias courts utilisés par le frontend Angular.</li>
 * </ul>
 * <p>
 * Identification des employés : utilise l'ID Oracle (Long) extrait du JWT via
 * {@link #extractEmployeeId(java.security.Principal)} ou depuis le principal STOMP
 * via {@link #extractEmployeeIdFromStomp(java.security.Principal)}.
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class ChatController {

    private final ChatService                       chatService;
    private final SimpMessageSendingOperations      messagingTemplate;
    private final FileStorageService                fileStorageService;
    private final KeycloakAdminService              keycloakAdminService;
    private final ConversationParticipantRepository partRepo;
    /* ── Conversations ────────────────────────────────── */

    /**
     * Retourne la liste des conversations actives de l'employé connecté,
     * triées par date du dernier message (plus récent en premier).
     *
     * @param principal le principal de sécurité JWT de l'utilisateur connecté
     * @return {@code 200 OK} avec la liste des {@link ConversationDTO}
     */
    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationDTO>> getMesConversations(
            Principal principal) {
        Long empId = extractEmployeeId(principal);
        return ResponseEntity.ok(chatService.getMesConversations(empId));
    }

    /**
     * Crée ou récupère une conversation directe.
     * Accepte deux formes :
     *   - Body JSON : { "otherEmployeeId": 5 }
     *   - Query params : ?user2Id={keycloakUUID} (utilisé par le composant Angular)
     */
    @PostMapping("/conversations")
    public ResponseEntity<ConversationDTO> creerOuRecupererConversation(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(required = false) String user2Id,
            Principal principal) {

        Long emp1Id = extractEmployeeId(principal);
        Long emp2Id = null;

        if (user2Id != null && !user2Id.isBlank()) {
            emp2Id = keycloakAdminService.findEmployeeIdByKeycloakId(user2Id).orElse(null);
        } else if (body != null) {
            emp2Id = toLong(body.get("otherEmployeeId"));
        }

        if (emp2Id == null) return ResponseEntity.badRequest().build();

        return ResponseEntity.ok(
                chatService.getOuCreerConversationDirecte(emp1Id, emp2Id));
    }

    /**
     * Crée un groupe.
     * Body : { "name": "...", "participantIds": [2, 3, 4] }
     */
    @PostMapping("/conversations/groupe")
    public ResponseEntity<ConversationDTO> creerGroupe(
            @RequestBody CreateConversationDTO req,
            Principal principal) {

        Long creatorId = extractEmployeeId(principal);
        return ResponseEntity.ok(
                chatService.creerGroupe(creatorId, req.getName(), req.getParticipantIds()));
    }

    /* ── Messages ─────────────────────────────────────── */

    /**
     * Retourne tous les messages non supprimés d'une conversation, triés chronologiquement.
     * Déclenche également le marquage automatique comme lus des messages non lus.
     *
     * @param conversationId l'identifiant Oracle de la conversation
     * @param principal      le principal de sécurité de l'utilisateur connecté
     * @return {@code 200 OK} avec la liste des {@link MessageDTO}
     * @throws SecurityException si l'utilisateur n'est pas participant actif de la conversation
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<MessageDTO>> getMessages(
            @PathVariable Long conversationId,
            Principal principal) {
        Long empId = extractEmployeeId(principal);
        try {
            return ResponseEntity.ok(chatService.getMessages(conversationId, empId));
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    /**
     * Envoi REST (fallback si WebSocket non disponible).
     * Body : { "content": "...", "type": "TEXTE", "replyToId": null }
     */
    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<MessageDTO> envoyerMessageRest(
            @PathVariable Long conversationId,
            @RequestBody EnvoiMessageDTO envoi,
            Principal principal) {

        Long senderId = extractEmployeeId(principal);

        MessageDTO dto = chatService.envoyerMessage(
                conversationId,
                senderId,
                envoi.getContent(),
                envoi.getType(),
                envoi.getAttachmentUrl(),
                envoi.getReplyToId());

        // Notifier via WebSocket les autres participants
        broadcastToConversation(conversationId, dto, senderId);

        return ResponseEntity.ok(dto);
    }

    /** Quitter / supprimer une conversation (soft-delete pour l'appelant). */
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> quitterConversation(
            @PathVariable Long conversationId,
            Principal principal) {
        Long empId = extractEmployeeId(principal);
        try {
            chatService.quitterConversation(conversationId, empId);
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    /** Marquer une conversation comme lue */
    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Void> marquerLu(
            @PathVariable Long conversationId,
            Principal principal) {
        Long empId = extractEmployeeId(principal);
        try {
            chatService.getMessages(conversationId, empId);
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    /* ── WebSocket ────────────────────────────────────── */

    /**
     * Reçoit un message via WebSocket STOMP et le diffuse à tous les participants.
     * <p>
     * {@code @MessageMapping("/chat.envoyer")} : destination STOMP complète {@code /app/chat.envoyer}.
     * <br>
     * {@code @Payload} : extrait le corps STOMP désérialisé en {@link EnvoiMessageDTO}.
     * <p>
     * Le message est persisté en base via {@link com.gerai.chat.service.ChatService#envoyerMessage},
     * puis diffusé via topic et files personnelles par {@link #broadcastToConversation}.
     *
     * @param envoi     le DTO contenant conversationId, contenu, type et éventuellement replyToId
     * @param principal le principal STOMP ({@link WebSocketAuthChannelInterceptor.StompPrincipal})
     */
    @MessageMapping("/chat.envoyer")
    public void envoyerMessageWs(@Payload EnvoiMessageDTO envoi, Principal principal) {

        Long senderId = extractEmployeeIdFromStomp(principal);
        if (senderId == null) {
            log.warn("[Chat-WS] Impossible d'extraire employee_id du principal STOMP");
            return;
        }

        MessageDTO dto = chatService.envoyerMessage(
                envoi.getConversationId(),
                senderId,
                envoi.getContent(),
                envoi.getType(),
                envoi.getAttachmentUrl(),
                envoi.getReplyToId());

        broadcastToConversation(envoi.getConversationId(), dto, senderId);
    }

    /**
     * Reçoit un indicateur de frappe via WebSocket STOMP et le transmet au destinataire.
     * <p>
     * {@code @MessageMapping("/chat.typing")} : destination STOMP complète {@code /app/chat.typing}.
     * <p>
     * L'indicateur est envoyé directement à l'employé destinataire via sa file personnelle
     * {@code /user/{destinataireEmployeeId}/queue/typing}.
     *
     * @param typingDTO le DTO contenant conversationId, destinataireEmployeeId et l'état de frappe
     * @param principal le principal STOMP de l'expéditeur
     */
    @MessageMapping("/chat.typing")
    public void typing(@Payload TypingDTO typingDTO, Principal principal) {
        Long senderId = extractEmployeeIdFromStomp(principal);
        messagingTemplate.convertAndSendToUser(
                typingDTO.getDestinataireEmployeeId().toString(),
                "/queue/typing",
                new TypingResponseDTO(senderId, typingDTO.getConversationId(), typingDTO.isTyping()));
    }

    /* ── Upload fichier ───────────────────────────────── */

    /**
     * Téléverse un fichier (image ou document) dans une conversation et crée un message de type
     * {@code IMAGE} ou {@code FICHIER} selon le content-type du fichier.
     * Le fichier est stocké physiquement via {@link com.gerai.chat.service.FileStorageService}.
     *
     * @param conversationId l'identifiant Oracle de la conversation cible
     * @param file           le fichier multipart à téléverser
     * @param principal      le principal de sécurité de l'émetteur
     * @return {@code 200 OK} avec le {@link MessageDTO} du message créé, incluant l'URL du fichier
     */
    @PostMapping("/conversations/{conversationId}/upload")
    public ResponseEntity<MessageDTO> uploadFile(
            @PathVariable Long conversationId,
            @RequestParam("file") MultipartFile file,
            Principal principal) {

        Long senderId = extractEmployeeId(principal);
        String fileUrl = fileStorageService.storeFile(file);
        String type = file.getContentType() != null
                && file.getContentType().startsWith("image") ? "IMAGE" : "FICHIER";

        MessageDTO dto = chatService.envoyerFichier(conversationId, senderId, fileUrl, type);
        broadcastToConversation(conversationId, dto, senderId);

        return ResponseEntity.ok(dto);
    }

    /* ── Alias endpoints (chemins courts utilisés par Angular) ─── */

    /**
     * POST /api/chat/send — alias pour /conversations/{id}/messages.
     * Body: { conversationId, content, type, attachmentUrl, replyToId }
     */
    @PostMapping("/send")
    public ResponseEntity<MessageDTO> envoyerMessageAlias(
            @RequestBody EnvoiMessageDTO envoi,
            Principal principal) {

        if (envoi.getConversationId() == null) return ResponseEntity.badRequest().build();
        Long senderId = extractEmployeeId(principal);

        MessageDTO dto = chatService.envoyerMessage(
                envoi.getConversationId(), senderId, envoi.getContent(),
                envoi.getType(), envoi.getAttachmentUrl(), envoi.getReplyToId());

        broadcastToConversation(envoi.getConversationId(), dto, senderId);
        return ResponseEntity.ok(dto);
    }

    /**
     * POST /api/chat/upload — alias pour /conversations/{id}/upload.
     * Form params: file, conversationId, destinataireId (ignoré).
     */
    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageDTO> uploadFileAlias(
            @RequestParam("file") MultipartFile file,
            @RequestParam("conversationId") Long conversationId,
            Principal principal) {

        Long senderId = extractEmployeeId(principal);
        String fileUrl = fileStorageService.storeFile(file);
        String type = file.getContentType() != null
                && file.getContentType().startsWith("image") ? "IMAGE" : "FICHIER";

        MessageDTO dto = chatService.envoyerFichier(conversationId, senderId, fileUrl, type);
        broadcastToConversation(conversationId, dto, senderId);
        return ResponseEntity.ok(dto);
    }

    /**
     * POST /api/chat/read/{conversationId} — alias pour /conversations/{id}/read.
     */
    @PostMapping("/read/{conversationId}")
    public ResponseEntity<Void> marquerLuAlias(
            @PathVariable Long conversationId,
            Principal principal) {

        Long empId = extractEmployeeId(principal);
        try {
            chatService.getMessages(conversationId, empId);
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    /* ── Helpers ──────────────────────────────────────── */

    /**
     * Envoie un message à tous les participants d'une conversation via WebSocket.
     * Utilise l'employee_id Oracle converti en String comme identifiant STOMP.
     */
    private void broadcastToConversation(Long conversationId, MessageDTO dto, Long senderEmpId) {
        // Topic broadcast: for users actively viewing this conversation
        messagingTemplate.convertAndSend("/topic/conversation/" + conversationId, dto);

        // Personal queue: delivers to every participant regardless of active conversation
        partRepo.findByConversation_ConversationId(conversationId).forEach(p ->
            messagingTemplate.convertAndSendToUser(
                p.getEmployeeId().toString(),
                "/queue/messages",
                dto));
    }

    /**
     * Extrait l'employee_id Oracle depuis le JWT.
     * Ordre de priorité :
     *  1. Claim "employee_id" dans le JWT
     *  2. Colonne USER_ID dans EMPLOYEES (Keycloak sub UUID)
     *  3. Colonne EMAIL dans EMPLOYEES
     */
    private Long extractEmployeeId(Principal principal) {
        if (principal instanceof JwtAuthenticationToken jwtToken) {
            Jwt jwt = jwtToken.getToken();

            // 1. Claim explicite
            Object val = jwt.getClaim("employee_id");
            if (val instanceof Number n) return n.longValue();
            if (val instanceof String s) {
                try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
            }

            // 2. Lookup par Keycloak sub UUID (colonne USER_ID)
            String sub = jwt.getSubject();
            if (sub != null) {
                Optional<Long> byKcId = keycloakAdminService.findEmployeeIdByKeycloakId(sub);
                if (byKcId.isPresent()) return byKcId.get();
            }

            // 3. Lookup par email
            String email = jwt.getClaim("email");
            if (email != null) {
                Optional<Long> byEmail = keycloakAdminService.findEmployeeIdByEmail(email);
                if (byEmail.isPresent()) return byEmail.get();
            }

            throw new RuntimeException(
                    "ID Oracle introuvable pour sub=" + sub + " email=" + email);
        }
        throw new RuntimeException("Principal JWT invalide ou manquant");
    }

    /**
     * Extrait l'employee_id Oracle depuis le principal STOMP.
     * Pour les WebSockets, l'intercepteur a déjà fait le travail de vérification.
     */
    private Long extractEmployeeIdFromStomp(Principal principal) {
        if (principal instanceof WebSocketAuthChannelInterceptor.StompPrincipal stomp) {
            try {
                return Long.parseLong(stomp.getEmployeeId());
            } catch (NumberFormatException e) {
                log.error("[Chat-WS] employee_id malformé dans le principal STOMP");
            }
        }
        return null;
    }

    /**
     * Utilitaire de conversion sécurisée vers Long.
     */
    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }}