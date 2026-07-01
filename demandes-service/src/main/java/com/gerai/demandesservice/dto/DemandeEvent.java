package com.gerai.demandesservice.dto;

import lombok.*;

/**
 * Événement analytique publié vers le service d'analytics ({@code analytics-service})
 * lors d'un changement de statut de demande de congé ou d'un refus.
 * <p>
 * Envoyé via un appel REST POST à {@code /internal/events} du analytics-service,
 * permettant la mise à jour des tableaux de bord RH en temps réel.
 *
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemandeEvent {

    /** Identifiant Oracle de l'employé destinataire (EMPLOYEES.EMPLOYEE_ID sous forme de chaîne). */
    private String  destinataireId;
    /** Type de la demande concernée (ex : {@code CONGE}, {@code PRET}, {@code FORMATION}). */
    private String  typeDemande;
    /** Statut Oracle de la demande après traitement (ex : {@code VALIDE_RH}, {@code REFUSE}). */
    private String  statut;
    /** Identifiant de la demande concernée (clé primaire de la table spécifique). */
    private String  referenceId;
    /** Nombre de jours concernés (pour les congés — {@code DAYS_COUNT}). */
    private Integer nbJours;
    /** Nom du microservice émetteur (toujours {@code DEMANDES-SERVICE}). */
    private String  sourceService;
}
