package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.CampagneEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data JPA pour la gestion des campagnes d'évaluation.
 * <p>
 * Fournit les opérations CRUD standard sur la table {@code CAMPAGNES_EVALUATION}.
 * </p>
 *
 * @since 1.0
 */
public interface CampagneEvaluationRepository extends JpaRepository<CampagneEvaluation, Long> {}
