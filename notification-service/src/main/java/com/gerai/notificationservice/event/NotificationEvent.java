package com.gerai.notificationservice.event;

import lombok.*;

/**
 * Objet de transfert des données d'un événement de notification consommé depuis Kafka.
 * <p>
 * Produit par les microservices {@code demandes-service} et {@code employe-service}
 * et consommé par {@code notification-service} via le topic {@code notification-events}.
 * </p>
 * <p>
 * {@code @Builder} (Lombok) : permet la construction fluide des instances, notamment
 * dans les tests et dans l'endpoint de test email.<br>
 * {@code @ToString} (Lombok) : facilite la journalisation des événements reçus.
 * </p>
 * <p>
 * Le champ {@code referenceId} est de type {@link String} pour éviter les erreurs
 * de désérialisation Jackson lorsque les producteurs envoient des références
 * alphanumériques (ex. : {@code REF-123}).
 * </p>
 *
 * @since 1.0
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

    /**
     * Rôle destinataire pour la diffusion broadcast (ADMIN, CHEF, EMPLOYE).
     * Utilisé quand employeeId est null — diffuse à tous les utilisateurs du rôle via WebSocket.
     * La notification n'est PAS persistée en base dans ce cas.
     */
    private String role;
}