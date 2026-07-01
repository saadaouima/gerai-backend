package com.gerai.notificationservice.entity;

import com.gerai.notificationservice.enums.TypeNotification;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entité JPA représentant une notification persistée dans la table Oracle {@code NOTIFICATIONS}.
 * <p>
 * {@code @Entity} : déclare cette classe comme entité JPA mappée sur une table de base de données.<br>
 * {@code @Table(name = "NOTIFICATIONS")} : spécifie le nom exact de la table Oracle cible.<br>
 * {@code @Builder} (Lombok) : permet la construction fluide des instances via le pattern Builder.<br>
 * {@code @ToString} (Lombok) : génère une représentation textuelle utile pour les logs.
 * </p>
 * <p>
 * Une notification peut être de deux natures :
 * <ul>
 *   <li><strong>Personnelle</strong> : {@code employeeId} renseigné, {@code role} null.</li>
 *   <li><strong>Broadcast</strong> : {@code employeeId} null, {@code role} renseigné
 *       (ADMIN, CHEF ou EMPLOYE).</li>
 * </ul>
 * L'horodatage de création est géré par la contrainte Oracle {@code DEFAULT SYSTIMESTAMP}
 * (non insérable ni modifiable par JPA).
 * </p>
 *
 * @since 1.0
 */
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

    /** Identifiant technique Oracle auto-incrémenté (clé primaire de la table NOTIFICATIONS). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NOTIFICATION_ID", nullable = false, updatable = false)
    private Long notificationId;

    /* ───────────────────────────────────────────── */
    /* 🔹 Relation Employé                          */
    /* ───────────────────────────────────────────── */

    /** Identifiant Oracle de l'employé destinataire ({@code null} pour les broadcasts de rôle). */
    @Column(name = "EMPLOYEE_ID")
    private Long employeeId;

    /** Rôle destinataire pour les notifications broadcast ; valeurs possibles : ADMIN, CHEF, EMPLOYE.
     *  Null pour les notifications personnelles (employeeId renseigné). */
    @Column(name = "ROLE", length = 20)
    private String role;

    /* ───────────────────────────────────────────── */
    /* 🔹 Type                                      */
    /* ───────────────────────────────────────────── */

    /** Type sémantique de la notification stocké sous forme de chaîne (ex. : NOUVELLE_DEMANDE). */
    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false, length = 50)
    private TypeNotification type;

    /* ───────────────────────────────────────────── */
    /* 🔹 Contenu                                   */
    /* ───────────────────────────────────────────── */

    /** Titre court affiché dans la liste des notifications de l'interface Angular. */
    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    /** Corps complet du message de notification (stocké en LOB pour les contenus longs). */
    @Lob
    @Column(name = "CONTENT")
    private String content;

    /* ───────────────────────────────────────────── */
    /* 🔹 Référence métier                          */
    /* ───────────────────────────────────────────── */

    /** Type de la ressource métier déclenchant la notification (ex. : DEMANDE, FORMATION, CONGE). */
    @Column(name = "REFERENCE_TYPE", length = 50)
    private String referenceType;

    /** Identifiant Oracle de la ressource métier associée (colonne NUMBER, type Long pour compatibilité Oracle). */
    @Column(name = "REFERENCE_ID")
    private Long referenceId;

    /** URL Angular vers laquelle l'employé sera redirigé en cliquant sur la notification. */
    @Column(name = "ACTION_URL", length = 500)
    private String actionUrl;

    /* ───────────────────────────────────────────── */
    /* 🔹 Etat lecture                              */
    /* ───────────────────────────────────────────── */

    /** Indique si l'employé a consulté cette notification (type Boolean pour compatibilité Lombok et NUMBER(1,0) Oracle). */
    @Builder.Default
    @Column(name = "IS_READ", nullable = false)
    private Boolean isRead = false;

    /** Horodatage exact auquel l'employé a marqué la notification comme lue ({@code null} si non lue). */
    @Column(name = "READ_AT")
    private LocalDateTime readAt;

    /* ───────────────────────────────────────────── */
    /* 🔹 Audit                                     */
    /* ───────────────────────────────────────────── */

    /** Identifiant Oracle de l'employé ayant déclenché l'action à l'origine de cette notification (audit). */
    @Column(name = "TRIGGERED_BY")
    private Long triggeredBy;

    /**
     * Horodatage de création de la notification.
     * Géré exclusivement par la contrainte {@code DEFAULT SYSTIMESTAMP} côté Oracle
     * (non insérable ni modifiable par JPA).
     */
    @Column(name = "CREATED_AT", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

}