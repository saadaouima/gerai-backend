package com.gerai.demandesservice.dto;

import lombok.*;

/**
 * DTO de message de notification publié sur le topic Kafka {@code notification-events}.
 * <p>
 * Utilisé en interne dans le {@code demandes-service} comme format alternatif
 * (plus léger que {@link NotificationEvent}) pour certains scénarios de notification.
 * <p>
 * {@code @Data} : génère getters, setters, {@code equals}, {@code hashCode} et {@code toString} via Lombok.
 * {@code @Builder} : expose un constructeur fluent Lombok.
 *
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {

    /** UUID Keycloak ({@code USER_ID}) du destinataire de la notification. */
    private String destinataireId;

    /** Rôle fonctionnel du destinataire (ex : {@code EMPLOYE}, {@code CHEF}, {@code RH}). */
    private String role;

    /** Adresse email du destinataire (utilisée si l'UUID ne suffit pas). */
    private String email;

    /** Titre court de la notification (affiché dans l'interface Angular). */
    private String titre;

    /** Corps du message de notification. */
    private String message;

    /**
     * Type sémantique Kafka correspondant à l'étape du workflow.
     * Valeurs possibles : {@code EN_ATTENTE}, {@code VALIDEE_CHEF}, {@code VALIDEE_RH},
     * {@code REJETEE}, {@code ANNULEE}.
     */
    private String type;

    /** Identifiant Oracle de la demande source ({@code REQUEST_ID} de la table concernée). */
    private String referenceId;

    /** Type de la demande concernée : {@code CONGE}, {@code FORMATION}, {@code PRET}, {@code DOCUMENT} ou {@code AUTORISATION}. */
    private String typeDemande;

    /** Nom du microservice émetteur (toujours {@code "DEMANDES-SERVICE"} ici). */
    private String sourceService;

    /** Valeur Oracle brute du statut final (ex : {@code VALIDE_RH}, {@code REFUSE}). */
    private String statut;

    /** Nombre de jours de congé concernés — renseigné uniquement pour les demandes de type CONGE. */
    private Integer nbJours;
}