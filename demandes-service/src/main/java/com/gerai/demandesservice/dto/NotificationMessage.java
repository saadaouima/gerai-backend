package com.gerai.demandesservice.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {

    private String destinataireId;  // UUID Keycloak du destinataire
    private String role;
    private String email;
    private String titre;
    private String message;

    /**
     * Type sémantique Kafka.
     * Valeurs : EN_ATTENTE | VALIDEE_CHEF | VALIDEE_RH | REJETEE | ANNULEE
     */
    private String type;

    private String referenceId;    // request_id de la table source
    private String typeDemande;    // CONGE | FORMATION | PRET | DOCUMENT | AUTORISATION
    private String sourceService;  // "DEMANDES-SERVICE"
    private String statut;         // valeur Oracle (VALIDE_RH, REFUSE, etc.)
    private Integer nbJours;       // pour ABSENCE_STATS (congés seulement)
}