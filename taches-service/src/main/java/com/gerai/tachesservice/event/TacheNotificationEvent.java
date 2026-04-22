package com.gerai.tachesservice.event;

import lombok.*;

/**
 * Événement Kafka publié par taches-service vers notification-service.
 *
 * Structure IDENTIQUE à NotificationEvent côté notification-service
 * pour que la désérialisation JSON fonctionne sans mapping de type.
 *
 * Scénarios couverts :
 *
 *   1. TACHE_ASSIGNEE   → notification à l'EMPLOYÉ assigné
 *      titre : "Nouvelle tâche : {titre}"
 *      message : "Vous avez été assigné(e) à la tâche..."
 *
 *   2. TACHE_MODIFIEE   → notification à l'EMPLOYÉ si la tâche change
 *
 *   3. TACHE_CLOTUREE   → notification au CHEF créateur du projet
 *      titre : "Tâche terminée : {titre}"
 *      message : "{prénom} a terminé la tâche..."
 *
 *   4. TACHE_EN_RETARD  → notification au CHEF si une tâche dépasse l'échéance
 *
 *   5. TACHE_CREEE      → notification au CHEF (confirmation de création)
 *
 * Champs communs avec NotificationEvent :
 *   employeeId   → NOTIFICATIONS.employee_id (Long Oracle)
 *   type         → NOTIFICATIONS.type (CHECK Oracle)
 *   title        → NOTIFICATIONS.title
 *   content      → NOTIFICATIONS.content
 *   referenceId  → NOTIFICATIONS.reference_id (task_id en String)
 *   referenceType→ NOTIFICATIONS.reference_type
 *   actionUrl    → NOTIFICATIONS.action_url
 *   email        → pour EmailService côté notification-service
 *   sourceService→ "TACHES-SERVICE"
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class TacheNotificationEvent {

    /**
     * ID Oracle de l'employé destinataire (EMPLOYEES.employee_id).
     * Utilisé par notification-service pour persister et pousser via WebSocket.
     */
    private Long   employeeId;

    /**
     * Adresse email du destinataire — utilisée par EmailService.
     */
    private String email;

    /**
     * Type de notification — doit correspondre aux valeurs
     * du CHECK Oracle sur NOTIFICATIONS.type :
     *   NOUVELLE_DEMANDE | DEMANDE_APPROUVEE | DEMANDE_REJETEE |
     *   NOUVEAU_MESSAGE  | RAPPEL | INFO | SYSTEME
     *
     * Pour les tâches on utilisera INFO (affectation) et RAPPEL (retard).
     */
    private String type;

    /** Titre de la notification affiché dans le centre de notifications Angular */
    private String title;

    /** Corps complet du message */
    private String content;

    /**
     * ID de référence de l'entité source.
     * Ici : task_id en String (notification-service le parse en Long).
     */
    private String referenceId;

    /**
     * Type de l'entité référencée — ex : "TACHE"
     * Affiché par Angular pour router vers le bon composant.
     */
    private String referenceType;

    /**
     * URL Angular de l'action associée.
     * Ex : "/chef/taches?id=42"
     */
    private String actionUrl;

    /** UUID Keycloak — pour le routing STOMP côté notification-service */
    private String keycloakSub;

    /** Service émetteur — filtrage côté consommateur */
    @Builder.Default
    private String sourceService = "TACHES-SERVICE";

    /** Rôle du destinataire : EMPLOYE | CHEF | RH */
    private String role;
}