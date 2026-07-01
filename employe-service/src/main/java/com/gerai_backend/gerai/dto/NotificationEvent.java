package com.gerai_backend.gerai.dto;

import lombok.*;

/**
 * DTO Kafka publié par {@code employe-service} vers le topic {@code notification-events},
 * consommé par le {@code notification-service}.
 *
 * <p>La structure de ce DTO doit correspondre exactement à celle de {@code NotificationEvent}
 * dans le {@code notification-service} pour assurer la désérialisation correcte.</p>
 *
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    /** Identifiant Oracle de l'employé concerné par la notification. */
    private Long    employeeId;

    /** Adresse email du destinataire (si envoi email activé). */
    private String  email;

    /** Type de notification : {@code INFO}, {@code ALERTE}, {@code SUCCES}, etc. */
    private String  type;

    /** Titre court de la notification affiché dans l'interface. */
    private String  title;

    /** Corps détaillé du message de notification. */
    private String  content;

    /** Type de l'entité référencée (ex. {@code EMPLOYE}, {@code CONGE}). */
    private String  referenceType;

    /** Identifiant de l'entité référencée (en tant que chaîne pour l'interopérabilité). */
    private String  referenceId;

    /** URL de navigation vers l'entité concernée dans le frontend Angular. */
    private String  actionUrl;

    /** Identifiant Oracle de l'utilisateur qui a déclenché la notification. */
    private Long    triggeredBy;

    /** Nom du microservice émetteur de l'événement (ex. {@code EMPLOYE-SERVICE}). */
    private String  sourceService;

    /** Indique si un email doit également être envoyé au destinataire. */
    private Boolean sendEmail;

    /** Rôle Keycloak ciblé par la notification (ex. {@code ADMIN}, {@code RH}). */
    private String  role;
}
