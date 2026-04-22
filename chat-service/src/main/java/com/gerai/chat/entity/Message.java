package com.gerai.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entité mappée sur MESSAGES (V4__communication.sql).
 *
 * Différences avec l'ancienne entité :
 *  - PK : message_id (IDENTITY Oracle — plus de séquence MSG_SEQ)
 *  - sender_id : Long (employee_id Oracle), plus String Keycloak
 *  - content : CLOB (plus contenu VARCHAR2(2000))
 *  - type : VARCHAR2(30) — TEXTE | IMAGE | FICHIER | SYSTEME
 *  - reply_to_id : auto-référence pour les fils de discussion
 *  - is_deleted : soft delete
 *  - sent_at remplace date_envoi
 *  - Plus de statut ENVOYE/LU côté message — géré par MESSAGE_READS
 */
@Entity
@Table(name = "MESSAGES")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MESSAGE_ID")
    private Long messageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CONVERSATION_ID", nullable = false)
    private Conversation conversation;

    /** ID Oracle de l'expéditeur (FK EMPLOYEES) */
    @Column(name = "SENDER_ID", nullable = false)
    private Long senderId;

    /**
     * Nom de l'expéditeur — non stocké en DB.
     * Calculé au moment du mapping DTO via JOIN EMPLOYEES.
     */
    @Transient
    private String senderNom;

    @Lob
    @Column(name = "CONTENT", nullable = false)
    private String content;

    /**
     * Type de message : TEXTE | IMAGE | FICHIER | SYSTEME
     * (contrainte CHECK dans V4__communication.sql)
     */
    @Column(name = "TYPE", length = 30)
    @Builder.Default
    private String type = "TEXTE";

    @Column(name = "ATTACHMENT_URL", length = 500)
    private String attachmentUrl;

    /** Référence au message parent (pour les réponses) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REPLY_TO_ID")
    private Message replyTo;

    /** Soft delete — jamais supprimé physiquement */
    @Column(name = "IS_DELETED", nullable = false)
    @Builder.Default
    private Integer isDeleted = 0;

    @Column(name = "SENT_AT", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "EDITED_AT")
    private LocalDateTime editedAt;

    @PrePersist
    protected void onCreate() {
        this.sentAt = LocalDateTime.now();
        if (this.type == null) this.type = "TEXTE";
        if (this.isDeleted == null) this.isDeleted = 0;
    }
}