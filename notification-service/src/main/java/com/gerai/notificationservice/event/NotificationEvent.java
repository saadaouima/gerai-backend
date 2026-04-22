package com.gerai.notificationservice.event;

import lombok.*;

/**
 * Événement Kafka consommé par notification-service.
 * * Note : referenceId est passé en String pour éviter les erreurs de désérialisation
 * quand les sources envoient des références alphanumériques (ex: REF-123).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class NotificationEvent {

    /* ── Destinataire ─────────────────────────────── */

    /** ID Oracle de l'employé destinataire (EMPLOYEES.employee_id) */
    private Long employeeId;

    /** Adresse email pour l'envoi SMTP (non stockée en DB) */
    private String email;

    /* ── Contenu ──────────────────────────────────── */

    /**
     * Type sémantique (doit matcher TypeNotification enum) :
     * NOUVELLE_DEMANDE, DEMANDE_APPROUVEE, DEMANDE_REJETEE, etc.
     */
    private String type;

    private String title;

    private String content;

    /* ── Référence métier ─────────────────────────── */

    /** Type de la ressource source : DEMANDE, CONGE, FORMATION, MESSAGE... */
    private String referenceType;

    /** * ID de la ressource source.
     * CHANGEMENT : Utilisation de String pour supporter les IDs mixtes et éviter l'erreur
     * 'Cannot deserialize Long from String' vue dans les logs.
     */
    private String referenceId;

    /** URL vers l'interface Angular */
    private String actionUrl;

    /* ── Audit ────────────────────────────────────── */

    /** ID de l'employé ayant déclenché l'action */
    private Long triggeredBy;

    /* ── Métadonnées Kafka ────────────────────────── */

    /** Service émetteur (ex: "DEMANDES-SERVICE") */
    private String sourceService;

    /* ── Champs additionnels pour Kafka ──────────────── */

    /** Optionnel : permet de savoir si on doit aussi envoyer un email */
    private Boolean sendEmail;

    /** Nombre de jours (utile pour les stats ou rappels de congés) */
    private Integer nbJours;
}