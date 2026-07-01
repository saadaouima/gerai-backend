package com.gerai.notificationservice.dto;

import com.gerai.notificationservice.enums.TypeNotification;
import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO de réponse retourné par l'API REST des notifications.
 * <p>
 * Aligné sur l'entité {@link com.gerai.notificationservice.entity.Notification}
 * et la table {@code NOTIFICATIONS} Oracle. Sérialisé en JSON vers le frontend Angular
 * et transmis via WebSocket STOMP.
 * </p>
 *
 * @since 1.0
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDTO {

    /** Identifiant technique Oracle de la notification (clé primaire). */
    private Long   notificationId;

    /** Identifiant Oracle de l'employé destinataire ({@code null} pour les broadcasts de rôle). */
    private Long   employeeId;

    /** Rôle destinataire pour les notifications broadcast (ADMIN, CHEF, EMPLOYE) ;
     *  {@code null} pour les notifications personnelles. */
    private String role;

    /** Type sémantique de la notification (détermine la couleur et l'icône dans le frontend). */
    private TypeNotification type;

    /** Titre de la notification affiché dans l'interface. */
    private String  title;

    /** Corps détaillé du message de notification. */
    private String  content;

    /** Type de la ressource métier associée (ex. : DEMANDE, FORMATION, MESSAGE). */
    private String  referenceType;

    /** Identifiant Oracle de la ressource métier associée. */
    private Long    referenceId;

    /** URL Angular permettant de naviguer vers la ressource associée. */
    private String  actionUrl;

    /** Indique si l'employé a déjà consulté cette notification. */
    private Boolean isRead;

    /** Horodatage de lecture de la notification ({@code null} si non lue). */
    private LocalDateTime readAt;

    /** Horodatage de création de la notification (géré par {@code DEFAULT SYSTIMESTAMP} Oracle). */
    private LocalDateTime createdAt;

    /** Identifiant Oracle de l'employé ayant déclenché l'action à l'origine de cette notification. */
    private Long    triggeredBy;
}