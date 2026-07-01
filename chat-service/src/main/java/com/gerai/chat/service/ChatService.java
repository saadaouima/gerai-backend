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
 * Service métier principal du microservice chat-service.
 * <p>
 * {@code @Service} : déclare ce bean comme service Spring géré par le conteneur IoC.
 * <br>
 * {@code @Slf4j} (Lombok) : injecte un logger SLF4J pour la traçabilité des opérations.
 * <p>
 * Responsabilités :
 * <ul>
 *   <li>Gestion du cycle de vie des conversations (directes et groupes).</li>
 *   <li>Envoi et lecture de messages texte et fichiers.</li>
 *   <li>Marquage automatique des messages comme lus lors de l'ouverture d'une conversation.</li>
 *   <li>Mapping entre entités JPA ({@link com.gerai.chat.entity.Conversation},
 *       {@link com.gerai.chat.entity.Message}) et DTOs de présentation.</li>
 * </ul>
 * <p>
 * L'injection de {@link PresenceService} est différée ({@code @Lazy}) pour briser
 * le cycle de dépendance circulaire avec {@link KeycloakAdminService}.
 *
 * @since 1.0
 */
@Slf4j
@Service
public class ChatService {

    /** Repository d'accès aux conversations. */
    private final ConversationRepository convRepo;

    /** Repository d'accès aux messages. */
    private final MessageRepository msgRepo;

    /** Repository d'accès aux participants des conversations. */
    private final ConversationParticipantRepository partRepo;

    /** Repository de suivi de la lecture des messages. */
    private final MessageReadRepository readRepo;

    /** Service de résolution des noms et IDs d'employés via Keycloak et Oracle. */
    private final KeycloakAdminService keycloakService;

    /** Service de présence en temps réel des utilisateurs connectés. */
    private final PresenceService presenceService;

    /**
     * Constructeur avec injection manuelle pour gérer le {@code @Lazy} sur {@link PresenceService}
     * et éviter les cycles de dépendances Spring au démarrage.
     *
     * @param convRepo        repository des conversations
     * @param msgRepo         repository des messages
     * @param partRepo        repository des participants
     * @param readRepo        repository de la lecture des messages
     * @param keycloakService service Keycloak/Oracle pour la résolution des identités
     * @param presenceService service de présence (injection différée)
     */
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

    /**
     * Retourne la liste des conversations actives de l'employé, triées par activité décroissante.
     *
     * @param employeeId l'identifiant Oracle de l'employé connecté
     * @return la liste des {@link ConversationDTO} de l'employé (liste vide si aucune conversation)
     */
    @Transactional(readOnly = true)
    public List<ConversationDTO> getMesConversations(Long employeeId) {
        return convRepo.findAllByEmployeeId(employeeId)
                .stream()
                .map(c -> toConversationDTO(c, employeeId))
                .collect(Collectors.toList());
    }

    /**
     * Récupère une conversation directe existante entre deux employés,
     * ou en crée une nouvelle si elle n'existe pas.
     * <p>
     * Implémente un mécanisme de récupération des états incohérents (conversation
     * partiellement créée lors d'une transaction précédente échouée).
     *
     * @param emp1Id identifiant Oracle du premier employé (initiateur)
     * @param emp2Id identifiant Oracle du second employé (destinataire)
     * @return le {@link ConversationDTO} de la conversation directe, créé ou existant
     */
    @Transactional
    public ConversationDTO getOuCreerConversationDirecte(Long emp1Id, Long emp2Id) {
        // 1. Standard lookup: conversation with both participants
        List<Conversation> existantes = convRepo.findAllDirectConversations(emp1Id, emp2Id);
        if (!existantes.isEmpty()) {
            return toConversationDTO(existantes.get(0), emp1Id);
        }

        // 2. Recovery: find any DIRECT conversation where emp1 is participant
        //    to handle partial state from a previous failed transaction
        for (Conversation c : convRepo.findAllByEmployeeId(emp1Id)) {
            if (!"DIRECT".equals(c.getType())) continue;
            List<ConversationParticipant> parts =
                    partRepo.findByConversation_ConversationId(c.getConversationId());
            boolean hasEmp2 = parts.stream().anyMatch(p -> emp2Id.equals(p.getEmployeeId()));
            if (hasEmp2) {
                return toConversationDTO(c, emp1Id);
            }
            if (parts.size() == 1 && emp1Id.equals(parts.get(0).getEmployeeId())) {
                // Half-created conversation: add the missing participant
                partRepo.save(ConversationParticipant.builder()
                        .conversation(c).employeeId(emp2Id).role("MEMBRE").build());
                return toConversationDTO(c, emp1Id);
            }
        }

        // 3. Create a new conversation
        Conversation conv = Conversation.builder()
                .type("DIRECT")
                .createdBy(emp1Id)
                .isActive(1)
                .lastMessageAt(LocalDateTime.now())
                .build();
        conv = convRepo.save(conv);

        safeAddParticipant(conv, emp1Id);
        safeAddParticipant(conv, emp2Id);

        return toConversationDTO(conv, emp1Id);
    }

    /**
     * Ajoute un participant à une conversation de manière idempotente.
     * N'insère pas si le participant existe déjà (évite les doublons en cas de retry).
     *
     * @param conv       la conversation à laquelle ajouter le participant
     * @param employeeId l'identifiant Oracle de l'employé à ajouter
     */
    private void safeAddParticipant(Conversation conv, Long employeeId) {
        if (partRepo.findByConversation_ConversationIdAndEmployeeId(
                conv.getConversationId(), employeeId).isEmpty()) {
            partRepo.save(ConversationParticipant.builder()
                    .conversation(conv).employeeId(employeeId).role("MEMBRE").build());
        }
    }

    /**
     * Crée une nouvelle conversation de groupe avec le créateur comme administrateur.
     *
     * @param creatorId      identifiant Oracle de l'employé créateur (rôle {@code ADMIN})
     * @param name           nom du groupe
     * @param participantIds liste des identifiants Oracle des autres membres (rôle {@code MEMBRE})
     * @return le {@link ConversationDTO} du groupe nouvellement créé
     */
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

    /**
     * Retourne tous les messages actifs d'une conversation et marque automatiquement
     * les messages non lus comme lus pour l'employé demandeur.
     * <p>
     * Vérifie d'abord que l'employé est bien participant actif de la conversation
     * avant tout accès aux données.
     *
     * @param conversationId l'identifiant Oracle de la conversation
     * @param employeeId     l'identifiant Oracle de l'employé connecté
     * @return la liste des {@link MessageDTO} triés chronologiquement (du plus ancien au plus récent)
     * @throws SecurityException si l'employé n'est pas participant actif de la conversation
     */
    /**
     * Quitte (supprime) une conversation du point de vue de l'employé connecté.
     * Pose {@code leftAt = now()} sur la participation — la conversation disparaît
     * de la liste de l'utilisateur sans affecter l'autre participant.
     *
     * @param conversationId identifiant Oracle de la conversation
     * @param empId          identifiant Oracle de l'employé qui quitte
     * @throws SecurityException si l'employé n'est pas participant de cette conversation
     */
    @Transactional
    public void quitterConversation(Long conversationId, Long empId) {
        ConversationParticipant part = partRepo
                .findByConversation_ConversationIdAndEmployeeId(conversationId, empId)
                .orElseThrow(() -> new SecurityException("Vous n'êtes pas participant de cette conversation"));
        part.setLeftAt(LocalDateTime.now());
        partRepo.save(part);
    }

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

    /**
     * Persiste un nouveau message dans une conversation et met à jour l'horodatage
     * du dernier message de la conversation.
     * <p>
     * Vérifie que l'expéditeur est participant actif avant la persistance.
     *
     * @param conversationId l'identifiant Oracle de la conversation destinataire
     * @param senderId       l'identifiant Oracle de l'expéditeur
     * @param content        le contenu textuel du message
     * @param type           le type du message : {@code TEXTE}, {@code IMAGE} ou {@code FICHIER}
     * @param attachmentUrl  l'URL relative de la pièce jointe (null pour les messages texte)
     * @param replyToId      l'identifiant Oracle du message auquel on répond (null si aucune réponse)
     * @return le {@link MessageDTO} du message persisté
     * @throws SecurityException    si l'expéditeur n'est pas participant actif
     * @throws RuntimeException     si la conversation est introuvable en base
     */
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

    /**
     * Convertit une entité {@link com.gerai.chat.entity.Conversation} en {@link ConversationDTO}.
     * <p>
     * Calcule dynamiquement :
     * <ul>
     *   <li>Le nom de la conversation (nom de l'autre participant pour les conversations directes).</li>
     *   <li>L'aperçu du dernier message (tronqué à 50 caractères).</li>
     *   <li>Le nombre de messages non lus.</li>
     *   <li>Le statut en ligne de chaque participant via {@link PresenceService}.</li>
     * </ul>
     *
     * @param c                 l'entité conversation à convertir
     * @param currentEmployeeId l'identifiant Oracle de l'employé courant (pour le comptage des non-lus)
     * @return le DTO de présentation de la conversation
     */
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

    /**
     * Convertit une entité {@link com.gerai.chat.entity.Message} en {@link MessageDTO}.
     * <p>
     * Calcule {@code luParMoi} : {@code true} si l'utilisateur courant est l'expéditeur
     * ou si un enregistrement de lecture existe dans {@code MESSAGE_READS}.
     * Remplace le contenu par "Message supprimé" si {@code isDeleted = 1}.
     *
     * @param m                 l'entité message à convertir
     * @param currentEmployeeId l'identifiant Oracle de l'employé courant (pour le statut de lecture)
     * @return le DTO de présentation du message
     */
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