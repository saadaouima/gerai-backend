package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.JourFerie;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data JPA pour la gestion des jours fériés.
 * <p>
 * Fournit les opérations CRUD standard sur la table {@code REF_JOURS_FERIES}.
 * </p>
 *
 * @since 1.0
 */
public interface JourFerieRepository extends JpaRepository<JourFerie, Long> {}
