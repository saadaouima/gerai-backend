package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.GrilleSalariale;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data JPA pour la gestion de la grille salariale.
 * <p>
 * Fournit les opérations CRUD standard sur la table {@code REF_GRILLE_SALARIALE}.
 * </p>
 *
 * @since 1.0
 */
public interface GrilleSalarialeRepository extends JpaRepository<GrilleSalariale, Long> {}
