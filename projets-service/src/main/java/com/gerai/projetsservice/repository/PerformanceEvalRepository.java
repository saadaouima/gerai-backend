package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository Spring Data JPA pour la gestion des évaluations de performance.
 * <p>
 * Fournit les opérations CRUD standard sur la table {@code PERFORMANCE_EVALS}
 * ainsi que des requêtes métier spécifiques pour le filtrage par employé, évaluateur et période.
 * </p>
 *
 * @since 1.0
 */
@Repository
public interface PerformanceEvalRepository extends JpaRepository<PerformanceEval, Long> {

    /**
     * Retourne les évaluations d'un employé, triées par année décroissante.
     *
     * @param employeeId identifiant Oracle de l'employé évalué
     * @return liste des évaluations de l'employé, de la plus récente à la plus ancienne
     */
    List<PerformanceEval> findByEmployeeIdOrderByPeriodYearDesc(Long employeeId);

    /**
     * Retourne les évaluations soumises par un évaluateur donné, triées par date décroissante.
     *
     * @param evaluatorId identifiant Oracle du chef de projet évaluateur
     * @return liste des évaluations soumises par cet évaluateur
     */
    List<PerformanceEval> findByEvaluatorIdOrderByCreatedAtDesc(Long evaluatorId);

    /**
     * Recherche une évaluation unique pour un employé sur une période spécifique.
     *
     * @param employeeId identifiant Oracle de l'employé
     * @param year       année de la période d'évaluation
     * @param quarter    trimestre ou type de période ({@code ANNUEL}, {@code T1}...)
     * @return l'évaluation correspondante si elle existe
     */
    Optional<PerformanceEval> findByEmployeeIdAndPeriodYearAndPeriodQuarter(
            Long employeeId, Integer year, String quarter);
}
