package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.TypeConge;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data JPA pour la gestion du référentiel des types de congé.
 * <p>
 * Fournit les opérations CRUD standard sur la table {@code REF_TYPES_CONGE}.
 * </p>
 *
 * @since 1.0
 */
public interface TypeCongeRepository extends JpaRepository<TypeConge, Long> {}
