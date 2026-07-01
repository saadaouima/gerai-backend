package com.gerai.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entité JPA mappée sur la table {@code MESSAGES}.
 * <p>
 * Représente un message envoyé dans une conversation. Supporte les types suivants :
 * <ul>
 *   <li>{@code TEXTE} : message texte ordinaire.</li>
 *   <li>{@code IMAGE} : image jointe (URL dans {@link #attachmentUrl}).</li>
 *   <li>{@code FICHIER} : document joint (URL dans {@link #attachmentUrl}).</li>
 *   <li>{@code SYSTEME} : message généré automatiquement (ex. "X a rejoint le groupe").</li>
 * </ul>
 * Le statut de lecture n'est pas stocké dans cette entité mais dans {@link MessageRead}
 * (table {@code MESSAGE_READS}), permettant de tracer la lecture par chaque participant.
 * <p>
 * Les messages ne sont jamais supprimés physiquement ({@code IS_DELETED = 1} pour le soft delete).
 * <p>
 * {@code @Entity} : déclare cette classe comme entité JPA persistée.
 * <br>
 * {@code @Table(name = "MESSAGES")} : mappe sur la table Oracle correspondante.
 * <br>
 * {@code @Lob} sur {@link #content} : stocke le contenu en {@code CLOB} Oracle.
 *
 * @since 1.0
 */
@Entity
@Table(name = "MESSAGES")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    /** Identifiant unique Oracle du message (clé primaire auto-incrémentée). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MESSAGE_ID")
    private Long messageId;

    /**
     * Conversation à laquelle appartient ce message.
     * Chargement paresseux pour éviter les requêtes inutiles.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CONVERSATION_ID", nullable = false)
    private Conversation conversation;

    /** ID Oracle de l'employé expéditeur (clé étrangère vers {@code EMPLOYEES}). */
    @Column(name = "SENDER_ID", nullable = false)
    private Long senderId;

    /**
     * Nom de l'expéditeur, non stocké en base de données.
     * Calculé via JOIN avec {@code EMPLOYEES} au moment du mapping DTO.
     */
    @Transient
    private String senderNom;

    /** Contenu textuel du message, stocké en {@code CLOB} Oracle pour les longs messages. */
    @Lob
    @Column(name = "CONTENT", nullable = false)
    private String content;

    /**
     * Type du message : {@code TEXTE}, {@code IMAGE}, {@code FICHIER} ou {@code SYSTEME}.
     * Contrainte {@code CHECK} définie dans le script SQL de migration.
     */
    @Column(name = "TYPE", length = 30)
    @Builder.Default
    private String type = "TEXTE";

    /** URL relative de la pièce jointe (ex. {@code /uploads/uuid_fichier.pdf}), null pour les messages texte. */
    @Column(name = "ATTACHMENT_URL", length = 500)
    private String attachmentUrl;

    /**
     * Message parent auquel celui-ci répond (pour les fils de discussion).
     * Null si ce message n'est pas une réponse.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REPLY_TO_ID")
    private Message replyTo;

    /**
     * Indicateur de suppression logique (soft delete).
     * {@code 1} = message supprimé (affiché comme "Message supprimé"), {@code 0} = actif.
     * Le message n'est jamais effacé physiquement de la base.
     */
    @Column(name = "IS_DELETED", nullable = false)
    @Builder.Default
    private Integer isDeleted = 0;

    /** Horodatage d'envoi du message, renseigné automatiquement par {@link #onCreate()}. */
    @Column(name = "SENT_AT", nullable = false)
    private LocalDateTime sentAt;

    /** Horodatage de la dernière modification du contenu (null si jamais modifié). */
    @Column(name = "EDITED_AT")
    private LocalDateTime editedAt;

    /**
     * Initialise automatiquement les champs de date et les valeurs par défaut
     * avant la première persistance en base.
     */
    @PrePersist
    protected void onCreate() {
        this.sentAt = LocalDateTime.now();
        if (this.type == null) this.type = "TEXTE";
        if (this.isDeleted == null) this.isDeleted = 0;
    }
}