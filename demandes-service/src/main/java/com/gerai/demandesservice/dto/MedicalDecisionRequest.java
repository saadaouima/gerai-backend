package com.gerai.demandesservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO reçu lors de la saisie de la décision du comité médical
 * sur un congé de longue maladie (type {@code LONGUE_MALADIE}).
 * <p>
 * Envoyé via {@code PUT /api/conges/{id}/decision-medicale} par le rôle RH ou ADMIN.
 *
 * @since 1.0
 */
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class MedicalDecisionRequest {
    /** {@code true} si le comité médical approuve le congé longue maladie, {@code false} sinon. */
    private boolean approuve;
    /** Commentaire du comité médical justifiant la décision (recommandé si refus). */
    private String  commentaire;
}
