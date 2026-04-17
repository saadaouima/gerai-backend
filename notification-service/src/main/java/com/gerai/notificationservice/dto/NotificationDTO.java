package com.gerai.notificationservice.dto;

import com.gerai.notificationservice.enums.TypeNotification;
import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO retourné par l'API REST.
 * Aligné sur l'entité Notification et la table NOTIFICATIONS Oracle.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDTO {

    private Long   notificationId;
    private Long   employeeId;

    private TypeNotification type;

    private String  title;
    private String  content;

    private String  referenceType;
    private Long    referenceId;
    private String  actionUrl;

    private Boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    private Long    triggeredBy;
}