package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.SoldeCongeAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data JPA pour la gestion des soldes de congés administratifs.
 * <p>
 * Fournit les opérations CRUD standard sur la table {@code SOLDES_CONGES_ADMIN}.
 * </p>
 *
 * @since 1.0
 */
public interface SoldeCongeAdminRepository extends JpaRepository<SoldeCongeAdmin, Long> {}
