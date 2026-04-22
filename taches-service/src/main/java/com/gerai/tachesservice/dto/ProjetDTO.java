package com.gerai.tachesservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Correspond exactement à l'interface Angular Projet :
 * {
 *   id, nom, description, dateDebut, datefin,
 *   statut, progression, chefProjet,
 *   membres: { id, nom, prenom, email, poste, departement }[]
 * }
 *
 * Source Oracle : PROJECTS + PROJECT_MEMBERS + EMPLOYEES + DEPARTMENTS
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjetDTO {

    private Long   id;

    /** PROJECTS.name */
    private String nom;

    /** PROJECTS.description */
    private String description;

    /** PROJECTS.code */
    private String code;

    /** PROJECTS.start_date */
    private LocalDate dateDebut;

    /** PROJECTS.end_date — Angular attend "datefin" (pas dateFin) */
    private LocalDate datefin;
    private Long createdBy;

    /**
     * PROJECTS.status — valeurs Oracle : PLANIFIE | EN_COURS | EN_PAUSE | TERMINE | ANNULE
     * Angular attend : Encours | Termine | Enretard | Enattente
     * La conversion est faite dans le mapper.
     */
    private String statut;

    /** PROJECTS.progress_pct */
    private Integer progression;

    /** "Prénom Nom" du créateur du projet */
    private String chefProjet;

    /**
     * Membres du projet — Angular les affiche dans la sidebar
     * et les propose dans le select "Assigner à"
     */
    private List<MembreDTO> membres;

    /* ── MembreDTO (record interne) ──────────────────── */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MembreDTO {
        private Long   id;        // EMPLOYEES.employee_id
        private String nom;       // EMPLOYEES.last_name
        private String prenom;    // EMPLOYEES.first_name
        private String email;     // EMPLOYEES.email
        private String poste;     // POSITIONS.title
        private String departement; // DEPARTMENTS.name
    }
}
