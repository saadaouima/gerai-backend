package com.gerai.tachesservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO unifié réponse — couvre les deux interfaces Angular :
 *
 * Tache (affectation-taches, espace Chef) :
 *   id, titre, projet, priorite, prioriteColor, echeance, assigneA
 *
 * TacheKanban (liste-taches, espace Employé) :
 *   id, titre, projet, priorite, prioriteColor, statut, echeance, progression
 *
 * Les champs null sont omis du JSON (@JsonInclude).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TacheDTO {

    /* ── Identité ──────────────────────────────────────── */
    private Long   id;

    /** TASKS.title */
    private String titre;

    /** Nom du projet parent (PROJECTS.name) */
    private String projet;

    /** ID du projet parent */
    private Long   projetId;

    /* ── Priorité ──────────────────────────────────────── */

    /**
     * TASKS.priority : FAIBLE | NORMALE | HAUTE | CRITIQUE
     * Converti en label Angular : Basse | Moyenne | Haute | Critique
     */
    private String priorite;

    /**
     * Couleur HEX calculée depuis priority — attendue par Angular :
     *   Haute    → #ff5370
     *   Moyenne  → #FFB64D
     *   Basse    → #2ed8b6
     *   Critique → #ff5370 (même couleur que Haute)
     */
    private String prioriteColor;

    /* ── Statut ──────────────────────────────────────── */

    /**
     * Statut Oracle : A_FAIRE | EN_COURS | EN_REVUE | TERMINE | BLOQUE
     * Angular Kanban : A_FAIRE | EN_COURS | TERMINEE
     * (EN_REVUE et BLOQUE mappés en EN_COURS pour le Kanban employé)
     */
    private String statut;

    /* ── Dates ───────────────────────────────────────── */

    /** TASKS.due_date — format dd/MM/yyyy attendu par | date pipe Angular */
    private LocalDate echeance;

    /** TASKS.created_at */
    private LocalDateTime dateCreation;

    /* ── Assignation ─────────────────────────────────── */

    /** "Prénom Nom" de l'assigné — attendu par getInitiales() Angular */
    private String  assigneA;

    /** TASKS.assigned_to (employee_id Oracle) */
    private Long    assigneId;

    /** TASKS.created_by */
    private Long    creePar;

    /* ── Progression ─────────────────────────────────── */

    /** TASKS.progress_pct NUMBER(3) 0-100 */
    private Integer progression;

    /* ── Description ─────────────────────────────────── */

    /** TASKS.description (CLOB) */
    private String description;

    /* ── Heures ──────────────────────────────────────── */
    private BigDecimal heuresEstimees;
    private BigDecimal heuresReelles;
}