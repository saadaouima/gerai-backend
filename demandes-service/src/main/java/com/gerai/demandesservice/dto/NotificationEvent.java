package com.gerai.demandesservice.dto;

import lombok.*;

/**
 * Événement de notification publié sur le topic Kafka {@code notification-events}.
 * <p>
 * Consommé par le {@code notification-service} qui l'affiche dans l'interface
 * et envoie optionnellement un email à l'employé ou au gestionnaire concerné.
 *
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    /** Identifiant Oracle de l'employé destinataire de la notification (EMPLOYEES.EMPLOYEE_ID). */
    private Long    employeeId;
    /** Adresse email du destinataire (pour l'envoi d'email si {@code sendEmail} est {@code true}). */
    private String  email;
    /** Type de notification : {@code INFO}, {@code RAPPEL}, {@code VALIDEE_CHEF}, {@code REJETEE}, etc. */
    private String  type;
    /** Titre court de la notification (affiché dans l'interface et le sujet de l'email). */
    private String  title;
    /** Corps de la notification (affiché dans l'interface et le corps de l'email). */
    private String  content;
    /** Identifiant de la demande RH concernée (clé primaire de la table spécifique). */
    private String  referenceId;
    /** Type de la ressource liée : {@code CONGE}, {@code PRET}, {@code FORMATION}, {@code DEPART}, etc. */
    private String  referenceType;
    /** URL de l'action correspondante dans le frontend Angular (ex : {@code /employe/demandes}). */
    private String  actionUrl;
    /** Nom du microservice émetteur (toujours {@code DEMANDES-SERVICE}). */
    private String  sourceService;
    /** {@code true} pour envoyer également un email en plus de la notification in-app. */
    private Boolean sendEmail;
    /** Nombre de jours de congé concernés (utilisé dans les messages de notification). */
    private Integer nbJours;
}
