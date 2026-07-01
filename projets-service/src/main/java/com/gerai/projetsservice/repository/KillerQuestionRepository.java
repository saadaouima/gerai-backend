package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.KillerQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Repository Spring Data JPA pour la gestion des killer questions de recrutement.
 * <p>
 * Fournit les opérations CRUD standard sur la table {@code KILLER_QUESTIONS}
 * ainsi que des requêtes spécifiques au filtrage et à la suppression par offre d'emploi.
 * </p>
 *
 * @since 1.0
 */
public interface KillerQuestionRepository extends JpaRepository<KillerQuestion, Long> {

    /**
     * Retourne les killer questions d'une offre d'emploi, triées par ordre d'affichage.
     *
     * @param jobId identifiant de l'offre d'emploi
     * @return liste des killer questions triées par {@code displayOrder} ascendant
     */
    List<KillerQuestion> findByJobIdOrderByDisplayOrderAsc(Long jobId);

    /**
     * Supprime toutes les killer questions associées à une offre d'emploi.
     *
     * @param jobId identifiant de l'offre d'emploi
     */
    @Transactional
    void deleteByJobId(Long jobId);
}
