package com.gerai.notificationservice.dto;

import com.gerai.notificationservice.enums.TypeNotification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * DTO de requête pour la création manuelle d'une notification via l'API REST.
 * <p>
 * Alignée sur les colonnes NOT NULL de la table {@code NOTIFICATIONS} Oracle.
 * Utilisée exclusivement par l'endpoint {@code POST /api/notifications}
 * réservé aux rôles RH, ADMIN et ADMIN_RH.
 * </p>
 *
 * @since 1.0
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateNotificationRequest {

    /** Identifiant Oracle de l'employé destinataire de la notification (obligatoire). */
    @NotNull(message = "L'ID de l'employé est obligatoire")
    private Long employeeId;

    /** Type sémantique de la notification déterminant l'affichage et la couleur (obligatoire). */
    @NotNull(message = "Le type est obligatoire")
    private TypeNotification type;

    /** Titre affiché dans l'interface de notification (obligatoire, non vide). */
    @NotBlank(message = "Le titre est obligatoire")
    private String title;

    /** Corps détaillé du message de notification (optionnel). */
    private String content;

    /* ── Référence métier (optionnel) ─────────────── */

    /** Type de la ressource métier associée : DEMANDE, MESSAGE, PROJET, etc. (optionnel). */
    private String referenceType;

    /** Identifiant Oracle de la ressource métier associée (optionnel). */
    private Long   referenceId;

    /** URL Angular de l'action liée à la notification (optionnel). */
    private String actionUrl;

    /** Identifiant Oracle de l'employé ayant déclenché la notification (audit, optionnel). */
    private Long triggeredBy;
}