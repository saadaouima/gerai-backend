package com.gerai.chat.controller;

import com.gerai.chat.config.WebSocketAuthChannelInterceptor;
import com.gerai.chat.dto.*;
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

    private final ChatService                  chatService;
    private final SimpMessageSendingOperations messagingTemplate;
    private final FileStorageService           fileStorageService;
    private final KeycloakAdminService keycloakAdminService;
    /* ── Conversations ────────────────────────────────── */

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationDTO>> getMesConversations(
            Principal principal) {
        Long empId = extractEmployeeId(principal);
        return ResponseEntity.ok(chatService.getMesConversations(empId));
    }

    /**
     * Crée ou récupère une conversation directe.
     * Body : { "otherEmployeeId": 5 }
     */
    @PostMapping("/conversations")
    public ResponseEntity<ConversationDTO> creerOuRecupererConversation(
            @RequestBody Map<String, Object> body,
            Principal principal) {

        Long emp1Id = extractEmployeeId(principal);
        Long emp2Id = toLong(body.get("otherEmployeeId"));

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

    /* ── Helpers ──────────────────────────────────────── */

    /**
     * Envoie un message à tous les participants d'une conversation via WebSocket.
     * Utilise l'employee_id Oracle converti en String comme identifiant STOMP.
     */
    private void broadcastToConversation(Long conversationId, MessageDTO dto, Long senderEmpId) {
        // Pour chaque participant connu via la DTO (ou requête dédiée)
        // On envoie à tous — côté Angular, chaque client écoute /user/{employeeId}/queue/messages
        // Le service détermine les participants de la conversation
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversationId,
                dto);
    }

    /**
     * Extrait l'employee_id Oracle depuis le claim JWT "employee_id".
     * Si absent (ancien token), effectue une recherche de secours via Keycloak Admin.
     */
    private Long extractEmployeeId(Principal principal) {
        if (principal instanceof JwtAuthenticationToken jwtToken) {
            Jwt jwt = jwtToken.getToken();
            Object val = jwt.getClaim("employee_id");

            // 1. Chemin normal : l'ID est dans le JWT
            if (val != null) {
                if (val instanceof Number n) return n.longValue();
                if (val instanceof String s) {
                    try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
                }
            }

            // 2. Chemin de secours : ID absent (Ancien token)
            String email = jwt.getClaim("email");
            log.info("[Chat] employee_id manquant dans le JWT pour {}. Tentative de récupération via Keycloak...", email);

            if (email != null) {
                // Appel au service Keycloak pour chercher l'attribut en base Keycloak
                return keycloakAdminService.findEmployeeIdByEmail(email)
                        .orElseThrow(() -> new RuntimeException("ID Oracle introuvable pour l'utilisateur : " + email));
            }
        }
        throw new RuntimeException("Impossible d'identifier l'employé (Principal non valide ou email manquant)");
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