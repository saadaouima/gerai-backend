package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.Interview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Repository Spring Data JPA pour la gestion des entretiens de recrutement.
 * <p>
 * Fournit les opérations CRUD standard sur la table {@code INTERVIEWS}
 * ainsi qu'une requête de filtrage par candidat.
 * </p>
 *
 * @since 1.0
 */
public interface InterviewRepository extends JpaRepository<Interview, Long> {

    /**
     * Retourne tous les entretiens associés à un candidat donné.
     *
     * @param candidatId identifiant du candidat
     * @return liste des entretiens du candidat
     */
    List<Interview> findByCandidatId(Long candidatId);
}
