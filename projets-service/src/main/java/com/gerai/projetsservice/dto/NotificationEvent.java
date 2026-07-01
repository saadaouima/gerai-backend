package com.gerai.projetsservice.dto;

import lombok.*;

/**
 * DTO Kafka publié par {@code projets-service} vers {@code notification-service}.
 * <p>
 * Doit correspondre exactement aux champs de {@code NotificationEvent} dans
 * {@code notification-service} (désérialisation JSON par nom de champ).
 * Topic cible : {@code notification-events} (même topic que {@code demandes-service}).
 * </p>
 *
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    /* ── Destinataire ─────────────────────────────── */

    /** ID Oracle de l'employé destinataire (EMPLOYEES.employee_id) */
    private Long   employeeId;

    /** Email pour l'envoi SMTP (récupéré depuis employee-service) */
    private String email;

    /* ── Contenu ──────────────────────────────────── */

    /**
     * Type sémantique — doit matcher TypeNotification dans notification-service :
     *   NOUVELLE_DEMANDE | DEMANDE_APPROUVEE | DEMANDE_REJETEE |
     *   NOUVEAU_MESSAGE  | RAPPEL | INFO
     *
     * Pour les projets on utilise INFO pour la plupart des cas.
     */
    private String type;

    /** Titre de la notification */
    private String title;

    /** Corps du message */
    private String content;

    /* ── Référence métier ─────────────────────────── */

    /** PROJET */
    private String referenceType;

    /**
     * ID du projet en String pour compatibilité avec le consumer
     * (notification-service a changé referenceId en String).
     */
    private String referenceId;

    /** URL vers la page Angular du projet */
    private String actionUrl;

    /* ── Audit ────────────────────────────────────── */

    /** ID Oracle de l'employé ayant déclenché l'action (chef, admin...) */
    private Long triggeredBy;

    /* ── Métadonnées Kafka ────────────────────────── */

    /** Filtre côté consumer pour ne traiter que les events pertinents */
    private String sourceService;

    /** Indique si une notification par email doit être envoyée en complément de la notification in-app. */
    private Boolean sendEmail;

    /** Rôle destinataire pour broadcast (ADMIN, CHEF, EMPLOYE). Utilisé quand employeeId est null. */
    private String role;
}