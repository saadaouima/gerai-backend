package com.gerai.demandesservice.dto;

import lombok.*;
import java.math.BigDecimal;

/**
 * DTO reçu lors de la décision finale du Directeur Général sur un crédit.
 * Envoyé via {@code PUT /api/demandes/credit/{id}/decision-dg}.
 * <p>
 * Le DG peut approuver le crédit (avec montant et tranches éventuellement ajustés)
 * ou le refuser en fournissant un motif.
 *
 * @since 1.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DgDecisionRequest {

    /** {@code true} si le DG approuve le crédit, {@code false} s'il le refuse. */
    private boolean    approuve;

    /** Montant final accordé en TND — peut différer du montant initialement demandé. */
    private BigDecimal montantApprouve;

    /** Nombre de mensualités de remboursement accordées. */
    private Integer    nbTranches;

    /** Commentaire du DG ou motif de refus (recommandé en cas de refus). */
    private String     commentaire;
}
