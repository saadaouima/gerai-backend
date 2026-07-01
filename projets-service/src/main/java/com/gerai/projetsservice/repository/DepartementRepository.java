package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.Departement;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data JPA pour la gestion des départements.
 * <p>
 * Fournit les opérations CRUD standard sur la table {@code ADMIN_DEPARTEMENTS}.
 * </p>
 *
 * @since 1.0
 */
public interface DepartementRepository extends JpaRepository<Departement, Long> {}
