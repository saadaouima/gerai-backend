package com.gerai.notificationservice.dto;

import com.gerai.notificationservice.enums.TypeNotification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Requête de création manuelle d'une notification via l'API REST.
 * Alignée sur les colonnes NOT NULL de la table NOTIFICATIONS Oracle.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateNotificationRequest {

    @NotNull(message = "L'ID de l'employé est obligatoire")
    private Long employeeId;

    @NotNull(message = "Le type est obligatoire")
    private TypeNotification type;

    @NotBlank(message = "Le titre est obligatoire")
    private String title;

    private String content;

    /* ── Référence métier (optionnel) ─────────────── */
    private String referenceType;   // DEMANDE, MESSAGE, PROJET...
    private Long   referenceId;
    private String actionUrl;

    /** ID Oracle de l'employé ayant déclenché la notification */
    private Long triggeredBy;
}