package com.gerai.tachesservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO unifié de réponse pour les tâches, couvrant les deux espaces Angular.
 * <p>
 * Utilisé dans deux contextes distincts :
 * <ul>
 *   <li><b>Espace Chef</b> ({@code affectation-taches}) : champs utilisés —
 *       id, titre, projet, priorite, prioriteColor, echeance, assigneA.</li>
 *   <li><b>Espace Employé</b> ({@code liste-taches}, Kanban) : champs utilisés —
 *       id, titre, projet, priorite, prioriteColor, statut, echeance, progression.</li>
 * </ul>
 * <p>
 * {@code @JsonInclude(NON_NULL)} : les champs null sont omis du JSON pour
 * alléger les réponses selon le contexte d'utilisation.
 * {@code @Builder} : permet la construction fluide des instances dans les mappers du service.
 *
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TacheDTO {

    /* ── Identité ──────────────────────────────────────── */

    /** Identifiant Oracle de la tâche (TASKS.task_id). */
    private Long   id;

    /** Titre de la tâche (TASKS.title). */
    private String titre;

    /** Nom du projet parent (PROJECTS.name), résolu via Feign. */
    private String projet;

    /** Identifiant Oracle du projet parent (TASKS.project_id). */
    private Long   projetId;

    /* ── Priorité ──────────────────────────────────────── */

    /**
     * Priorité de la tâche convertie en label Angular.
     * Oracle : FAIBLE | NORMALE | HAUTE | CRITIQUE.
     * Angular : Basse | Moyenne | Haute | Critique.
     */
    private String priorite;

    /**
     * Couleur HEX associée à la priorité, calculée par le service.
     * Valeurs : Haute → {@code #ff5370}, Moyenne → {@code #FFB64D}, Basse → {@code #2ed8b6}.
     */
    private String prioriteColor;

    /* ── Statut ──────────────────────────────────────── */

    /**
     * Statut de la tâche converti pour Angular.
     * Oracle : A_FAIRE | EN_COURS | EN_REVUE | TERMINE | BLOQUE.
     * Angular Kanban : A_FAIRE | EN_COURS | TERMINEE.
     * (EN_REVUE et BLOQUE sont mappés en EN_COURS ou A_FAIRE pour le Kanban employé.)
     */
    private String statut;

    /* ── Dates ───────────────────────────────────────── */

    /**
     * Date d'échéance de la tâche (TASKS.due_date).
     * Formatée par le pipe Angular {@code date:'dd/MM/yyyy'}.
     */
    private LocalDate echeance;

    /** Date et heure de création de la tâche (TASKS.created_at). */
    private LocalDateTime dateCreation;

    /* ── Assignation ─────────────────────────────────── */

    /**
     * Nom complet ("Prénom Nom") de l'employé assigné, résolu depuis EMPLOYEES.
     * Utilisé par la fonction {@code getInitiales()} dans Angular pour afficher l'avatar.
     */
    private String  assigneA;

    /** Identifiant Oracle de l'employé assigné (TASKS.assigned_to). */
    private Long    assigneId;

    /** Identifiant Oracle du créateur de la tâche (TASKS.created_by). */
    private Long    creePar;

    /* ── Progression ─────────────────────────────────── */

    /** Pourcentage de progression de la tâche (TASKS.progress_pct), entre 0 et 100. */
    private Integer progression;

    /* ── Description ─────────────────────────────────── */

    /** Description détaillée de la tâche (TASKS.description, type CLOB en Oracle). */
    private String description;

    /* ── Heures ──────────────────────────────────────── */

    /** Nombre d'heures estimées pour réaliser la tâche (TASKS.estimated_hours). */
    private BigDecimal heuresEstimees;

    /** Nombre d'heures réellement passées sur la tâche (TASKS.actual_hours). */
    private BigDecimal heuresReelles;
}