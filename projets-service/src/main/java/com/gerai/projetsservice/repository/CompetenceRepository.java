package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.Competence;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data JPA pour la gestion du référentiel des compétences.
 * <p>
 * Fournit les opérations CRUD standard sur la table {@code REF_COMPETENCES}.
 * </p>
 *
 * @since 1.0
 */
public interface CompetenceRepository extends JpaRepository<Competence, Long> {}
