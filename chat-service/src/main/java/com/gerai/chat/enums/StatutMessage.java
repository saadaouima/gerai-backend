package com.gerai.chat.enums;

/**
 * Énumération représentant le statut d'un message dans l'ancien modèle de données.
 * <p>
 * <b>Note :</b> Ce statut binaire est désormais remplacé par la table {@code MESSAGE_READS}
 * (entité {@link com.gerai.chat.entity.MessageRead}) qui permet de tracer la lecture
 * message par message et participant par participant.
 * Cet enum est conservé pour compatibilité mais n'est plus utilisé dans la logique principale.
 *
 * @since 1.0
 */
public enum StatutMessage {

    /** Le message a été envoyé mais pas encore lu par le destinataire. */
    ENVOYE,

    /** Le message a été lu par au moins un destinataire. */
    LU
}