package com.gerai.chat.service;

import com.gerai.chat.dto.*;
import com.gerai.chat.entity.*;
import com.gerai.chat.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service Chat complet adapté à Oracle.
 */
@Slf4j
@Service
public class ChatService {

    private final ConversationRepository convRepo;
    private final MessageRepository msgRepo;
    private final ConversationParticipantRepository partRepo;
    private final MessageReadRepository readRepo;
    private final KeycloakAdminService keycloakService;
    private final PresenceService presenceService;

    // Injection manuelle pour gérer le @Lazy et éviter les cycles
    public ChatService(ConversationRepository convRepo,
                       MessageRepository msgRepo,
                       ConversationParticipantRepository partRepo,
                       MessageReadRepository readRepo,
                       KeycloakAdminService keycloakService,
                       @Lazy PresenceService presenceService) {
        this.convRepo = convRepo;
        this.msgRepo = msgRepo;
        this.partRepo = partRepo;
        this.readRepo = readRepo;
        this.keycloakService = keycloakService;
        this.presenceService = presenceService;
    }

    /* ── CONVERSATIONS ────────────────────────────────── */

    @Transactional(readOnly = true)
    public List<ConversationDTO> getMesConversations(Long employeeId) {
        return convRepo.findAllByEmployeeId(employeeId)
                .stream()
                .map(c -> toConversationDTO(c, employeeId))
                .collect(Collectors.toList());
    }

    @Transactional
    public ConversationDTO getOuCreerConversationDirecte(Long emp1Id, Long emp2Id) {
        List<Conversation> existantes = convRepo.findAllDirectConversations(emp1Id, emp2Id);

        if (!existantes.isEmpty()) {
            return toConversationDTO(existantes.get(0), emp1Id);
        }

        Conversation conv = Conversation.builder()
                .type("DIRECT")
                .createdBy(emp1Id)
                .isActive(1)
                .lastMessageAt(LocalDateTime.now())
                .build();

        conv = convRepo.save(conv);

        partRepo.save(ConversationParticipant.builder().conversation(conv).employeeId(emp1Id).role("MEMBRE").build());
        partRepo.save(ConversationParticipant.builder().conversation(conv).employeeId(emp2Id).role("MEMBRE").build());

        return toConversationDTO(conv, emp1Id);
    }

    @Transactional
    public ConversationDTO creerGroupe(Long creatorId, String name, List<Long> participantIds) {
        Conversation conv = Conversation.builder()
                .type("GROUPE")
                .name(name)
                .createdBy(creatorId)
                .isActive(1)
                .lastMessageAt(LocalDateTime.now())
                .build();
        conv = convRepo.save(conv);

        partRepo.save(ConversationParticipant.builder().conversation(conv).employeeId(creatorId).role("ADMIN").build());

        for (Long pid : participantIds) {
            if (!pid.equals(creatorId)) {
                partRepo.save(ConversationParticipant.builder().conversation(conv).employeeId(pid).role("MEMBRE").build());
            }
        }
        return toConversationDTO(conv, creatorId);
    }

    /* ── MESSAGES ────────────────────────────────────── */

    @Transactional
    public List<MessageDTO> getMessages(Long conversationId, Long employeeId) {
        if (!partRepo.isActiveParticipant(conversationId, employeeId)) {
            throw new SecurityException("Accès interdit");
        }

        readRepo.markAllAsRead(conversationId, employeeId, LocalDateTime.now());

        partRepo.findByConversation_ConversationIdAndEmployeeId(conversationId, employeeId)
                .ifPresent(p -> {
                    p.setLastReadAt(LocalDateTime.now());
                    partRepo.save(p);
                });

        return msgRepo.findByConversationId(conversationId)
                .stream()
                .map(m -> toMessageDTO(m, employeeId))
                .collect(Collectors.toList());
    }

    @Transactional
    public MessageDTO envoyerMessage(Long conversationId, Long senderId, String content, String type, String attachmentUrl, Long replyToId) {
        if (!partRepo.isActiveParticipant(conversationId, senderId)) {
            throw new SecurityException("Accès interdit");
        }

        Conversation conv = convRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation introuvable"));

        Message.MessageBuilder builder = Message.builder()
                .conversation(conv)
                .senderId(senderId)
                .content(content)
                .type(type != null ? type : "TEXTE")
                .attachmentUrl(attachmentUrl)
                .sentAt(LocalDateTime.now());

        if (replyToId != null) {
            msgRepo.findById(replyToId).ifPresent(builder::replyTo);
        }

        Message saved = msgRepo.save(builder.build());
        conv.setLastMessageAt(saved.getSentAt());
        convRepo.save(conv);

        return toMessageDTO(saved, senderId);
    }

    /* ── MAPPERS ─────────────────────────────────────── */

    private ConversationDTO toConversationDTO(Conversation c, Long currentEmployeeId) {
        List<ConversationParticipant> parts = partRepo.findByConversation_ConversationId(c.getConversationId());

        List<ParticipantDTO> participantDTOs = parts.stream()
                .map(p -> ParticipantDTO.builder()
                        .employeeId(p.getEmployeeId())
                        .nomComplet(keycloakService.getNomByEmployeeId(p.getEmployeeId()))
                        .role(p.getRole())
                        .enLigne(presenceService != null && presenceService.estConnecte(p.getEmployeeId().toString()))
                        .build())
                .collect(Collectors.toList());

        // Déterminer le nom de la conversation
        String finalName = c.getName();
        if ("DIRECT".equals(c.getType())) {
            finalName = participantDTOs.stream()
                    .filter(p -> !p.getEmployeeId().equals(currentEmployeeId))
                    .map(ParticipantDTO::getNomComplet)
                    .findFirst()
                    .orElse("Contact inconnu");
        }

        // Aperçu du dernier message
        String apercu = "";
        List<Message> derniers = msgRepo.findLastMessage(c.getConversationId());
        if (!derniers.isEmpty()) {
            Message last = derniers.get(0);
            apercu = "IMAGE".equals(last.getType()) ? "📷 Image" :
                    "FICHIER".equals(last.getType()) ? "📎 Fichier" : last.getContent();
            if (apercu != null && apercu.length() > 50) apercu = apercu.substring(0, 47) + "...";
        }

        return ConversationDTO.builder()
                .conversationId(c.getConversationId())
                .type(c.getType())
                .name(finalName)
                .participants(participantDTOs)
                .dernierMessage(apercu)
                .lastMessageAt(c.getLastMessageAt())
                .nombreNonLus(msgRepo.countUnread(c.getConversationId(), currentEmployeeId))
                .currentEmployeeId(currentEmployeeId)
                .build();
    }

    private MessageDTO toMessageDTO(Message m, Long currentEmployeeId) {
        boolean luParMoi = currentEmployeeId.equals(m.getSenderId())
                || readRepo.existsByMessage_MessageIdAndEmployeeId(m.getMessageId(), currentEmployeeId);

        return MessageDTO.builder()
                .messageId(m.getMessageId())
                .conversationId(m.getConversation().getConversationId())
                .senderId(m.getSenderId())
                .senderNom(keycloakService.getNomByEmployeeId(m.getSenderId()))
                .content(m.getIsDeleted() == 1 ? "Message supprimé" : m.getContent())
                .type(m.getType())
                .attachmentUrl(m.getAttachmentUrl())
                .replyToId(m.getReplyTo() != null ? m.getReplyTo().getMessageId() : null)
                .isDeleted(m.getIsDeleted() == 1)
                .sentAt(m.getSentAt())
                .luParMoi(luParMoi)
                .build();
    }
    /**
     * Envoie un fichier ou une image dans une conversation.
     * Cette méthode réutilise envoyerMessage en adaptant le contenu et le type.
     */
    @Transactional
    public MessageDTO envoyerFichier(Long conversationId, Long senderId,
                                     String attachmentUrl, String type) {

        // Détermination du type de message (IMAGE ou FICHIER)
        String msgType = "IMAGE".equalsIgnoreCase(type) ? "IMAGE" : "FICHIER";

        // Texte par défaut pour l'aperçu dans la liste des conversations
        String content = "IMAGE".equals(msgType) ? "📷 Image" : "📎 Fichier";

        log.info("[Chat] Envoi de fichier | conv={} | sender={} | type={}",
                conversationId, senderId, msgType);

        return envoyerMessage(
                conversationId,
                senderId,
                content,
                msgType,
                attachmentUrl,
                null // Pas de réponse à un message spécifique par défaut ici
        );
    }
}