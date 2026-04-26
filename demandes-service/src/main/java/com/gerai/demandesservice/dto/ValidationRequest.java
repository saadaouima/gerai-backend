package com.gerai.demandesservice.dto;

import com.gerai.demandesservice.model.StatutDemande;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * DTO reçu lors d'une action de validation ou de rejet par Chef ou RH.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ValidationRequest {

    @NotNull(message = "Le nouveau statut est obligatoire")
    private StatutDemande nouveauStatut;   // VALIDEE_CHEF, VALIDEE_RH, REJETEE, ANNULEE

    /** Commentaire ou motif (obligatoire si REJETEE) */
    private String commentaire;
}