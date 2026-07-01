package com.gerai.demandesservice.dto;

import com.gerai.demandesservice.model.StatutDemande;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * DTO reçu lors d'une action de validation ou de rejet par un Chef ou un gestionnaire RH.
 * Envoyé via les endpoints {@code PUT /api/demandes/{id}/valider} et
 * {@code PUT /api/demandes/{type}/{id}/valider}.
 *
 * @since 1.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ValidationRequest {

    /** Nouveau statut cible souhaité — obligatoire ({@code VALIDEE_CHEF}, {@code VALIDEE_RH}, {@code REJETEE}, {@code ANNULEE}). */
    @NotNull(message = "Le nouveau statut est obligatoire")
    private StatutDemande nouveauStatut;

    /** Commentaire ou motif de refus — obligatoire si {@code nouveauStatut = REJETEE}. */
    private String commentaire;
}