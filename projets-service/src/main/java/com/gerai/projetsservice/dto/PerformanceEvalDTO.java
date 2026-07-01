package com.gerai.projetsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO pour la création et la consultation des évaluations de performance.
 * <p>
 * L'ordre des annotations Lombok est important pour la compatibilité entre Builder et JPA.
 * Transporté dans le body de {@code POST /api/admin/evals} et retourné par {@code GET /api/admin/evals}.
 * </p>
 *
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceEvalDTO {

    /** Identifiant unique de l'évaluation. */
    private Long    evalId;
    /** Identifiant Oracle de l'employé évalué. */
    private Long    employeeId;
    /** Identifiant Oracle du chef de projet évaluateur. */
    private Long    evaluatorId;
    /** Année de la période d'évaluation (ex. {@code 2024}). */
    private Integer periodYear;
    /** Trimestre ou type de période (ex. {@code ANNUEL}, {@code T1}, {@code T2}). */
    private String  periodQuarter;
    /** Score final calculé sur 100. */
    private Double  score;
    /** Points forts identifiés lors de l'évaluation. */
    private String  strengths;
    /** Points à améliorer identifiés lors de l'évaluation. */
    private String  improvements;
    /** Commentaires de l'évaluateur. */
    private String  comments;
    /** Statut du workflow d'évaluation (ex. {@code EN_ATTENTE}, {@code VALIDEE_CHEF}, {@code CLOTUREE}). */
    private String  status;
    /** Date et heure de création de l'évaluation. */
    private LocalDateTime createdAt;
}