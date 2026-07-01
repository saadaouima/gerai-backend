package com.gerai.demandesservice.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * DTO reçu lors de la soumission d'un vote par un membre du comité de crédit.
 * Envoyé via {@code POST /api/demandes/comite/{loanId}/vote}.
 *
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComiteVoteRequest {

    /** Décision du membre du comité : {@code FAVORABLE} ou {@code DEFAVORABLE}. */
    private String vote;

    /** Commentaire justifiant la décision du membre (optionnel). */
    private String commentaire;

    /** Montant de crédit suggéré par le membre (uniquement si vote {@code FAVORABLE}). */
    private BigDecimal montantSuggere;

    /** Nombre de mensualités de remboursement suggérées (uniquement si vote {@code FAVORABLE}). */
    private Integer nbTranchesSuggeres;
}
