package com.gerai.analyticsservice.event;

import lombok.*;

/**
 * Événement Kafka publié par le demandes-service et consommé par analytics-service.
 *
 * Statuts réels par table Oracle (base GERAI_USER) :
 *   LEAVE_REQUESTS         : EN_ATTENTE | VALIDE_CHEF | VALIDE_RH | REFUSE | ANNULE
 *   TRAINING_REQUESTS      : EN_ATTENTE | APPROUVE_CHEF | APPROUVE_RH | REFUSE | ANNULE
 *   LOAN_REQUESTS          : EN_ATTENTE | EN_ETUDE | APPROUVE | REFUSE | REMBOURSE
 *   DOCUMENT_REQUESTS      : EN_ATTENTE | EN_COURS | PRET | LIVRE | REFUSE
 *   AUTHORIZATION_REQUESTS : EN_ATTENTE | APPROUVE | REFUSE
 *
 * Valeurs de 'statut' utilisées dans StatsService.saveEvent() :
 *   "VALIDE_RH"  → congé validé par RH → incrémente NB_JOURS_CONGE dans ABSENCE_STATS
 *   "REFUSE"     → toute demande refusée → incrémente NB_REFUSEES dans ABSENCE_STATS
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class DemandeEvent {

    /**
     * ID Oracle de l'employé (EMPLOYEES.employee_id) en String.
     * Parsé en Long par StatsService.parseLong().
     * NE PAS confondre avec le UUID Keycloak (user_id).
     */
    private String destinataireId;

    /** Rôle du destinataire : EMPLOYE | CHEF | RH */
    private String role;

    /** Adresse email pour les notifications mail */
    private String email;

    /** Titre de la notification affichée dans l'appli */
    private String titre;

    /** Corps du message de notification */
    private String message;

    /**
     * Type d'événement Kafka (action déclenchante).
     * Exemples : DEMANDE_SOUMISE | DEMANDE_VALIDEE | DEMANDE_REJETEE
     * NE PAS confondre avec typeDemande (le type RH métier).
     */
    private String type;

    /**
     * ID de la demande dans sa table source (String pour flexibilité).
     * Exemple : "45" pour LEAVE_REQUESTS.request_id = 45
     */
    private String referenceId;

    /**
     * Type de demande RH (discriminant métier).
     * Valeurs : CONGE | FORMATION | PRET | DOCUMENT | AUTORISATION
     */
    private String typeDemande;

    /**
     * Statut atteint après traitement — dépend de la table source.
     * Valeurs utilisées par saveEvent() :
     *   "VALIDE_RH"  → màj ABSENCE_STATS (jours congé)
     *   "REFUSE"     → màj ABSENCE_STATS (compteur refus)
     */
    private String statut;

    /**
     * Nombre de jours de congé (LEAVE_REQUESTS.days_count).
     * Renseigné uniquement quand typeDemande = "CONGE" et statut = "VALIDE_RH".
     * Évite une requête Oracle supplémentaire dans saveEvent().
     */
    private Integer nbJours;

    /**
     * Service émetteur — filtré dans AnalyticsKafkaConsumer.
     * Valeur attendue : "DEMANDES-SERVICE"
     */
    private String sourceService;
}