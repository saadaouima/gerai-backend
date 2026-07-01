package com.gerai.tachesservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Objet de transfert de données représentant un projet, reçu depuis projets-service via Feign.
 * <p>
 * Ce DTO est la représentation côté taches-service des données de la table PROJECTS.
 * taches-service n'accède jamais directement à PROJECTS — ce DTO est l'unique source
 * d'information sur les projets dans ce microservice.
 * <p>
 * Correspond exactement à l'interface Angular {@code Projet} :
 * <pre>
 * {
 *   id, nom, description, dateDebut, datefin,
 *   statut, progression, chefProjet,
 *   membres: { id, nom, prenom, email, poste, departement }[]
 * }
 * </pre>
 * Source Oracle : tables PROJECTS + PROJECT_MEMBERS + EMPLOYEES + DEPARTMENTS.
 * <p>
 * {@code @JsonInclude(NON_NULL)} : les champs null sont omis du JSON pour alléger les réponses.
 *
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjetDTO {

    /** Identifiant Oracle du projet (PROJECTS.project_id). */
    private Long   id;

    /** Nom du projet (PROJECTS.name). */
    private String nom;

    /** Description détaillée du projet (PROJECTS.description). */
    private String description;

    /** Code alphanumérique unique du projet (PROJECTS.code). */
    private String code;

    /** Date de début du projet (PROJECTS.start_date). */
    private LocalDate dateDebut;

    /**
     * Date de fin prévue du projet (PROJECTS.end_date).
     * Nommé {@code datefin} (sans majuscule) pour correspondre exactement
     * au champ attendu par le frontend Angular.
     */
    private LocalDate datefin;

    /**
     * Identifiant Oracle de l'employé créateur du projet (PROJECTS.created_by).
     * Utilisé par {@code TacheNotificationProducer} pour identifier le chef
     * à notifier lors des changements de statut des tâches.
     */
    private Long createdBy;

    /**
     * Statut actuel du projet.
     * Valeurs Oracle : PLANIFIE | EN_COURS | EN_PAUSE | TERMINE | ANNULE.
     * Valeurs Angular : Encours | Termine | Enretard | Enattente.
     * La conversion est effectuée dans le mapper de projets-service.
     */
    private String statut;

    /** Pourcentage d'avancement du projet (PROJECTS.progress_pct), entre 0 et 100. */
    private Integer progression;

    /** Nom complet ("Prénom Nom") du créateur/chef du projet, calculé par projets-service. */
    private String chefProjet;

    /**
     * Liste des membres affectés au projet.
     * Affichée dans la sidebar Angular et proposée dans le select "Assigner à"
     * lors de la création d'une tâche.
     */
    private List<MembreDTO> membres;

    /**
     * DTO interne représentant un membre du projet.
     * <p>
     * Correspond à un employé affecté au projet via la table PROJECT_MEMBERS.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MembreDTO {

        /** Identifiant Oracle de l'employé (EMPLOYEES.employee_id). */
        private Long   id;

        /** Nom de famille de l'employé (EMPLOYEES.last_name). */
        private String nom;

        /** Prénom de l'employé (EMPLOYEES.first_name). */
        private String prenom;

        /** Adresse email professionnelle de l'employé (EMPLOYEES.email). */
        private String email;

        /** Intitulé du poste occupé (POSITIONS.title). */
        private String poste;

        /** Nom du département d'appartenance (DEPARTMENTS.name). */
        private String departement;
    }
}
