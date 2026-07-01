package com.gerai.demandesservice.repository;

import com.gerai.demandesservice.model.ComiteVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data JPA pour les votes du comité de crédit
 * (table {@code GERAI.COMITE_VOTES}).
 * <p>
 * {@code @Repository} : marque cette interface comme composant Spring de la couche données.
 *
 * @since 1.0
 */
@Repository
public interface ComiteVoteRepository extends JpaRepository<ComiteVote, Long> {

    /**
     * Retourne tous les votes enregistrés pour un crédit donné, triés par ordre chronologique.
     *
     * @param loanId identifiant du crédit (LOAN_REQUESTS.REQUEST_ID)
     * @return liste des votes triés par horodatage croissant
     */
    List<ComiteVote> findByLoanIdOrderByVotedAtAsc(Long loanId);

    /**
     * Vérifie si un membre a déjà voté pour un crédit donné.
     * Utilisé pour appliquer la contrainte d'unicité (un vote par membre par crédit).
     *
     * @param loanId   identifiant du crédit
     * @param memberId identifiant Oracle du membre du comité
     * @return {@code true} si ce membre a déjà voté pour ce crédit
     */
    boolean existsByLoanIdAndMemberId(Long loanId, Long memberId);

    /**
     * Compte le nombre de votes d'un type donné pour un crédit.
     * Utilisé par {@link com.gerai.demandesservice.service.ComiteService}
     * pour vérifier si le seuil de votes FAVORABLE ou DEFAVORABLE est atteint.
     *
     * @param loanId identifiant du crédit
     * @param vote   type de vote : {@code FAVORABLE} ou {@code DEFAVORABLE}
     * @return nombre de votes de ce type pour ce crédit
     */
    long countByLoanIdAndVote(Long loanId, String vote);
}
