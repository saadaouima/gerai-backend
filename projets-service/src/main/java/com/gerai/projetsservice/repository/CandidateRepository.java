package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Repository Spring Data JPA pour la gestion des candidats.
 * <p>
 * Fournit les opérations CRUD standard sur la table {@code CANDIDATES}
 * ainsi que des requêtes métier spécifiques au pipeline de recrutement.
 * </p>
 *
 * @since 1.0
 */
public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    /**
     * Retourne tous les candidats marqués comme shortlistés.
     *
     * @return liste des candidats avec {@code SHORTLISTE = true}
     */
    List<Candidate> findByShortlisteTrue();
}
