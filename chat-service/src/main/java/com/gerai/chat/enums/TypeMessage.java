package com.gerai.chat.enums;

/**
 * Énumération des types de messages supportés par le système de messagerie SYNAPSE.
 * <p>
 * <b>Note :</b> L'entité {@link com.gerai.chat.entity.Message} stocke le type
 * sous forme de {@code String} ({@code VARCHAR2(30)}) plutôt que d'utiliser cet enum directement,
 * afin de supporter également le type {@code SYSTEME} (messages générés automatiquement).
 *
 * @since 1.0
 */
public enum TypeMessage {

    /** Message textuel ordinaire entre employés. */
    TEXTE,

    /** Image jointe (JPEG, PNG, GIF, etc.), URL stockée dans {@code ATTACHMENT_URL}. */
    IMAGE,

    /** Document joint (PDF, Word, Excel, etc.), URL stockée dans {@code ATTACHMENT_URL}. */
    FICHIER
}