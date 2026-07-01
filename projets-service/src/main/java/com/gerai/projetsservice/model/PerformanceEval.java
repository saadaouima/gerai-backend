package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entité JPA représentant une évaluation individuelle de performance d'un employé.
 * <p>
 * Suit un workflow multi-acteurs : {@code EN_ATTENTE → AUTO_SOUMISE → VALIDEE_CHEF → VALIDEE_RH → CLOTUREE}.
 * Les métadonnées de l'employé évalué (nom, poste, département) sont stockées en JSON
 * dans {@code criteriaScores} pour éviter les jointures inter-services.
 * </p>
 * <p>
 * {@code @Entity} : classe persistée en base Oracle.<br>
 * {@code @Table(name = "PERFORMANCE_EVALS")} : nom de la table Oracle.<br>
 * {@code @PrePersist} : initialise {@code createdAt} à la date courante avant la première persistance.
 * </p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "PERFORMANCE_EVALS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PerformanceEval {

    /** Identifiant unique de l'évaluation (clé primaire générée). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EVAL_ID")
    private Long evalId;

    /** Identifiant Oracle de l'employé évalué ({@code EMPLOYEES.EMPLOYEE_ID}). */
    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    /** Identifiant Oracle du chef de projet évaluateur. */
    @Column(name = "EVALUATOR_ID", nullable = false)
    private Long evaluatorId;

    /** Année de la période d'évaluation. */
    @Column(name = "PERIOD_YEAR", nullable = false)
    private Integer periodYear;

    /** Période de l'évaluation : {@code ANNUEL}, {@code T1}, {@code T2}, {@code T3} ou {@code T4}. */
    @Column(name = "PERIOD_QUARTER", length = 6)
    private String periodQuarter;

    /** Score global de l'évaluation (0-100), calculé à partir des critères de notation. */
    @Column(name = "SCORE")
    private Double score;

    /** Métadonnées JSON : nom, prénom, poste, département et campagne de l'évaluation. */
    @Lob @Column(name = "CRITERIA_SCORES") private String criteriaScores;
    /** Points forts identifiés de l'employé. */
    @Lob @Column(name = "STRENGTHS")       private String strengths;
    /** Points à améliorer identifiés de l'employé. */
    @Lob @Column(name = "IMPROVEMENTS")    private String improvements;
    /** Commentaires globaux de l'évaluateur ou de la RH. */
    @Lob @Column(name = "COMMENTS")        private String comments;

    /** Statut du workflow d'évaluation (défaut : {@code BROUILLON}). */
    @Column(name = "STATUS", nullable = false, length = 20)
    @Builder.Default
    private String status = "BROUILLON";

    /** Date et heure de création de l'évaluation (initialisée automatiquement). */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Initialise la date de création à la date et l'heure courantes avant la première persistance.
     */
    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }
}