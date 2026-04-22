package com.gerai.notificationservice.entity;

import com.gerai.notificationservice.enums.TypeNotification;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "NOTIFICATIONS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Notification {

    /* ───────────────────────────────────────────── */
    /* 🔹 Primary Key                               */
    /* ───────────────────────────────────────────── */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NOTIFICATION_ID", nullable = false, updatable = false)
    private Long notificationId;

    /* ───────────────────────────────────────────── */
    /* 🔹 Relation Employé                          */
    /* ───────────────────────────────────────────── */

    @Column(name = "EMPLOYEE_ID")
    private Long employeeId;

    /* ───────────────────────────────────────────── */
    /* 🔹 Type                                      */
    /* ───────────────────────────────────────────── */

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false, length = 50)
    private TypeNotification type;

    /* ───────────────────────────────────────────── */
    /* 🔹 Contenu                                   */
    /* ───────────────────────────────────────────── */

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Lob
    @Column(name = "CONTENT")
    private String content;

    /* ───────────────────────────────────────────── */
    /* 🔹 Référence métier                          */
    /* ───────────────────────────────────────────── */

    @Column(name = "REFERENCE_TYPE", length = 50)
    private String referenceType;

    /** * IMPORTANT : Retour au type Long pour correspondre à la colonne NUMBER
     * de ta table Oracle sans changer la structure existante.
     */
    @Column(name = "REFERENCE_ID")
    private Long referenceId;

    @Column(name = "ACTION_URL", length = 500)
    private String actionUrl;

    /* ───────────────────────────────────────────── */
    /* 🔹 Etat lecture                              */
    /* ───────────────────────────────────────────── */

    /** * Utilisation de Boolean (Objet) pour le mapping avec NUMBER(1,0)
     * et compatibilité avec les getters/setters Lombok.
     */
    @Builder.Default
    @Column(name = "IS_READ", nullable = false)
    private Boolean isRead = false;

    @Column(name = "READ_AT")
    private LocalDateTime readAt;

    /* ───────────────────────────────────────────── */
    /* 🔹 Audit                                     */
    /* ───────────────────────────────────────────── */

    @Column(name = "TRIGGERED_BY")
    private Long triggeredBy;

    /**
     * Géré par DEFAULT SYSTIMESTAMP côté Oracle.
     */
    @Column(name = "CREATED_AT", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

}