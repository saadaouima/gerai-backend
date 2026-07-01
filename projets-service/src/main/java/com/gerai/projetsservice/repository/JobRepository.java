package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data JPA pour la gestion des offres d'emploi.
 * <p>
 * Fournit les opérations CRUD standard sur la table {@code ADMIN_JOBS}.
 * Le filtrage par statut ({@code OUVERT}, {@code FERME}...) est effectué
 * côté service ou contrôleur via stream Java.
 * </p>
 *
 * @since 1.0
 */
public interface JobRepository extends JpaRepository<Job, Long> {}
