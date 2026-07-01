package com.gerai.chat.dto;

import lombok.*;
import java.time.LocalDateTime;


/**
 * DTO de représentation d'un message dans l'interface chat.
 * <p>
 * Retourné dans les réponses REST et diffusé via WebSocket STOMP après chaque envoi.
 * Le champ {@code luParMoi} est calculé dynamiquement selon l'utilisateur courant.
 *
 * @since 1.0
 */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class MessageDTO {

    /** Identifiant unique Oracle du message (PK de MESSAGES). */
    private Long          messageId;

    /** Identifiant Oracle de la conversation à laquelle appartient ce message. */
    private Long          conversationId;

    /** ID Oracle de l'employé expéditeur. */
    private Long          senderId;

    /** Nom complet de l'expéditeur (résolu via JOIN EMPLOYEES au moment du mapping). */
    private String        senderNom;

    /** Contenu textuel du message (remplacé par "Message supprimé" si {@code isDeleted = true}). */
    private String        content;

    /** Type du message : {@code TEXTE}, {@code IMAGE}, {@code FICHIER} ou {@code SYSTEME}. */
    private String        type;

    /** URL relative de la pièce jointe (null pour les messages de type TEXTE). */
    private String        attachmentUrl;

    /** ID Oracle du message parent (si ce message est une réponse), null sinon. */
    private Long          replyToId;

    /** Indicateur de suppression logique (soft delete — le message n'est jamais effacé physiquement). */
    private boolean       isDeleted;

    /** Horodatage d'envoi du message. */
    private LocalDateTime sentAt;

    /** Horodatage de la dernière modification du message (null si jamais modifié). */
    private LocalDateTime editedAt;

    /** {@code true} si l'utilisateur courant a lu ce message (expéditeur = toujours true). */
    private boolean       luParMoi;
}