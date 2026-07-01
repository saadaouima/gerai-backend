package com.gerai.tachesservice.event;

import lombok.*;

/**
 * Événement Kafka publié par {@code taches-service} à destination de {@code notification-service}.
 * <p>
 * La structure de cet objet est identique à {@code NotificationEvent} côté notification-service,
 * ce qui permet la désérialisation JSON sans configuration de mapping de types.
 * <p>
 * Scénarios couverts par les notifications :
 * <ol>
 *   <li><b>TACHE_ASSIGNEE</b> → notification à l'EMPLOYÉ assigné :
 *       titre "Nouvelle tâche : {titre}", message "Vous avez été assigné(e)..."</li>
 *   <li><b>TACHE_MODIFIEE</b> → notification à l'EMPLOYÉ si la tâche est modifiée par le chef</li>
 *   <li><b>TACHE_CLOTUREE</b> → notification au CHEF créateur :
 *       titre "Tâche terminée : {titre}", message "{prénom} a terminé la tâche..."</li>
 *   <li><b>TACHE_EN_RETARD</b> → notification au CHEF si une tâche dépasse son échéance
 *       (émise par {@code TacheOverdueScheduler})</li>
 *   <li><b>TACHE_CREEE</b> → notification de confirmation au CHEF</li>
 * </ol>
 * <p>
 * Champs mappés vers la table NOTIFICATIONS Oracle :
 * employeeId, type, title, content, referenceId, referenceType, actionUrl.
 *
 * @since 1.0
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class TacheNotificationEvent {

    /**
     * Identifiant Oracle de l'employé destinataire de la notification (EMPLOYEES.employee_id).
     * Utilisé par notification-service pour persister la notification en base
     * et la pousser via WebSocket STOMP.
     */
    private Long   employeeId;

    /**
     * Adresse email du destinataire.
     * Transmise à {@code EmailService} côté notification-service pour l'envoi d'un email.
     */
    private String email;

    /**
     * Type de la notification — doit correspondre aux valeurs du CHECK Oracle
     * sur la colonne NOTIFICATIONS.type :
     * NOUVELLE_DEMANDE | DEMANDE_APPROUVEE | DEMANDE_REJETEE |
     * NOUVEAU_MESSAGE | RAPPEL | INFO | SYSTEME.
     * Pour les tâches : INFO (affectation, clôture) et RAPPEL (retard, modification).
     */
    private String type;

    /**
     * Titre court de la notification, affiché dans le centre de notifications Angular.
     * Limité pour l'affichage dans l'interface utilisateur.
     */
    private String title;

    /** Corps complet du message de notification, affiché au destinataire. */
    private String content;

    /**
     * Identifiant de l'entité source de la notification.
     * Contient le {@code task_id} en format String (notification-service le parse en Long).
     * Correspond à NOTIFICATIONS.reference_id.
     */
    private String referenceId;

    /**
     * Type de l'entité référencée (ex : {@code "TACHE"}).
     * Utilisé par Angular pour router vers le bon composant lors du clic sur la notification.
     * Correspond à NOTIFICATIONS.reference_type.
     */
    private String referenceType;

    /**
     * URL Angular de l'action associée à la notification.
     * Exemples : {@code "/chef/taches"}, {@code "/employe/taches"}.
     * Correspond à NOTIFICATIONS.action_url.
     */
    private String actionUrl;

    /**
     * UUID Keycloak de l'employé destinataire (EMPLOYEES.user_id).
     * Utilisé comme clé de routage STOMP par notification-service
     * pour le push WebSocket vers le bon client Angular connecté.
     */
    private String keycloakSub;

    /**
     * Identifiant du service émetteur de l'événement.
     * Valeur fixe : {@code "TACHES-SERVICE"}.
     * Permet le filtrage et le diagnostic côté consommateur Kafka.
     */
    @Builder.Default
    private String sourceService = "TACHES-SERVICE";

    /**
     * Rôle du destinataire de la notification.
     * Valeurs utilisées : {@code EMPLOYE} (assignation, modification) ou {@code CHEF} (clôture, retard).
     */
    private String role;
}