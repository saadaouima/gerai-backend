package com.gerai.chat.dto;

import lombok.*;


/**
 * DTO d'envoi d'un message dans une conversation.
 * <p>
 * Utilisé aussi bien pour les requêtes REST ({@code POST /api/chat/conversations/{id}/messages})
 * que pour les trames WebSocket STOMP sur la destination {@code /app/chat.envoyer}.
 *
 * @since 1.0
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class EnvoiMessageDTO {

    /** Identifiant Oracle de la conversation destinataire. */
    private Long   conversationId;

    /** Contenu textuel du message (obligatoire pour les messages de type TEXTE). */
    private String content;

    /** Type de message : {@code TEXTE}, {@code IMAGE} ou {@code FICHIER} (défaut : {@code TEXTE}). */
    private String type;

    /** URL relative du fichier joint (ex. {@code /uploads/uuid_fichier.pdf}), null si type TEXTE. */
    private String attachmentUrl;

    /** ID Oracle du message auquel celui-ci répond (null si ce n'est pas une réponse). */
    private Long   replyToId;
}