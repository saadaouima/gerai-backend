package com.gerai.demandesservice.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de réponse retourné après l'enregistrement d'un vote du comité de crédit.
 * Exposé par {@code POST /api/demandes/comite/{loanId}/vote}
 * et {@code GET /api/demandes/comite/{loanId}/votes}.
 *
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComiteVoteResponse {

    /** Identifiant technique du vote (COMITE_VOTES.VOTE_ID). */
    private Long voteId;
    /** Identifiant du crédit concerné (LOAN_REQUESTS.REQUEST_ID). */
    private Long loanId;
    /** Identifiant Oracle du membre du comité qui a voté (EMPLOYEES.EMPLOYEE_ID). */
    private Long memberId;
    /** Nom complet du membre du comité extrait du JWT au moment du vote. */
    private String memberNom;
    /** Décision : {@code FAVORABLE} ou {@code DEFAVORABLE}. */
    private String vote;
    /** Commentaire justifiant la décision du membre. */
    private String commentaire;
    /** Montant de crédit suggéré par ce membre (si FAVORABLE). */
    private BigDecimal montantSuggere;
    /** Nombre de mensualités suggérées par ce membre (si FAVORABLE). */
    private Integer nbTranchesSuggeres;
    /** Horodatage de l'enregistrement du vote. */
    private LocalDateTime votedAt;
}
