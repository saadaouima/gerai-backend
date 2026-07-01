package com.gerai.demandesservice.repository;

import com.gerai.demandesservice.model.Actif;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data JPA pour les actifs informatiques (table {@code GERAI.ACTIFS}).
 * <p>
 * Hérite de {@link JpaRepository} qui fournit les opérations CRUD standard :
 * {@code findAll()}, {@code findById()}, {@code save()}, {@code deleteById()}, etc.
 *
 * @since 1.0
 */
public interface ActifRepository extends JpaRepository<Actif, Long> {}
