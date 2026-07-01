package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.ScreeningQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Repository Spring Data JPA pour la gestion des questions de screening.
 * <p>
 * Fournit les opérations CRUD standard sur la table {@code SCREENING_QUESTIONS}
 * ainsi que des requêtes de filtrage et suppression par offre d'emploi.
 * </p>
 *
 * @since 1.0
 */
public interface ScreeningQuestionRepository extends JpaRepository<ScreeningQuestion, Long> {

    /**
     * Retourne les questions de screening d'une offre d'emploi, triées par ordre d'affichage.
     *
     * @param jobId identifiant de l'offre d'emploi
     * @return liste des questions de screening triées par {@code displayOrder} ascendant
     */
    List<ScreeningQuestion> findByJobIdOrderByDisplayOrderAsc(Long jobId);

    /**
     * Supprime toutes les questions de screening associées à une offre d'emploi.
     *
     * @param jobId identifiant de l'offre d'emploi
     */
    @Transactional
    void deleteByJobId(Long jobId);
}
