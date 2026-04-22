// ═══════════════════════════════════════════════════════════════════════════
//  StatutUpdateRequest.java — DTO pour PATCH /api/taches/{id} (drag & drop)
//  et PUT /api/taches/{id}/statut
// ═══════════════════════════════════════════════════════════════════════════
package com.gerai.tachesservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Corps de la requête de mise à jour de statut.
 *
 * Angular envoie depuis le drag&drop Kanban (ListeTachesComponent.onDrop()) :
 *   PATCH /api/taches/{id}
 *   Body : { statut: "EN_COURS", progression: 1 }
 *
 * Angular envoie depuis TacheService.updateStatut() :
 *   PUT /api/taches/{id}/statut
 *   Body : { statut: "TERMINEE" }
 *
 * Mapping Angular → Oracle :
 *   A_FAIRE  → A_FAIRE
 *   EN_COURS → EN_COURS
 *   TERMINEE → TERMINE  (la base Oracle utilise TERMINE sans E final)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatutUpdateRequest {

    @NotBlank(message = "Le statut est obligatoire")
    private String statut;

    /** Progression optionnelle (0-100) — mise à jour simultanée depuis le drag&drop */
    private Integer progression;
}