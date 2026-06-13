package com.gerai.chat.controller;

import com.gerai.chat.config.WebSocketAuthChannelInterceptor;
import com.gerai.chat.dto.*;
import com.gerai.chat.repository.ConversationParticipantRepository;
import com.gerai.chat.service.ChatService;
import com.gerai.chat.service.FileStorageService;
import com.gerai.chat.service.KeycloakAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Optional;
import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * ChatController adapté à la nouvelle base Oracle.
 *
 * Changements principaux :
 *  - extractEmployeeId() extrait le claim "employee_id" (Long Oracle) du JWT
 *    au lieu de travailler avec des UUID Keycloak strings
 *  - Les endpoints REST reçoivent/retournent des Long pour les IDs
 *  - Le WebSocket utilise toujours le sub Keycloak pour le routing STOMP
 *    mais passe l'employee_id Oracle au service métier
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

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<MessageDTO>> getMessages(
            @PathVariable Long conversationId,
            Principal principal) {
        Long empId = extractEmployeeId(principal);
        return ResponseEntity.ok(chatService.getMessages(conversationId, empId));
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

    /** Marquer une conversation comme lue */
    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Void> marquerLu(
            @PathVariable Long conversationId,
            Principal principal) {
        Long empId = extractEmployeeId(principal);
        chatService.getMessages(conversationId, empId); // déclenche le mark-as-read
        return ResponseEntity.noContent().build();
    }

    /* ── WebSocket ────────────────────────────────────── */

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

    @MessageMapping("/chat.typing")
    public void typing(@Payload TypingDTO typingDTO, Principal principal) {
        Long senderId = extractEmployeeIdFromStomp(principal);
        messagingTemplate.convertAndSendToUser(
                typingDTO.getDestinataireEmployeeId().toString(),
                "/queue/typing",
                new TypingResponseDTO(senderId, typingDTO.getConversationId(), typingDTO.isTyping()));
    }

    /* ── Upload fichier ───────────────────────────────── */

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
        chatService.getMessages(conversationId, empId);
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