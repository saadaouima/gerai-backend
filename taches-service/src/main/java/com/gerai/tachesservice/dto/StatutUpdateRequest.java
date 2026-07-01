// ═══════════════════════════════════════════════════════════════════════════
//  StatutUpdateRequest.java — DTO pour PATCH /api/taches/{id} (drag & drop)
//  et PUT /api/taches/{id}/statut
// ═══════════════════════════════════════════════════════════════════════════
package com.gerai.tachesservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * DTO représentant le corps de la requête de mise à jour du statut d'une tâche.
 * <p>
 * Utilisé par deux endpoints distincts :
 * <ul>
 *   <li>{@code PATCH /api/taches/{id}} — déclenché par le drag &amp; drop Kanban
 *       ({@code ListeTachesComponent.onDrop()}) avec le statut et la progression.</li>
 *   <li>{@code PUT /api/taches/{id}/statut} — mise à jour explicite du statut seul
 *       depuis {@code TacheService.updateStatut()}.</li>
 * </ul>
 * <p>
 * Mapping des statuts Angular vers les valeurs Oracle :
 * <ul>
 *   <li>{@code A_FAIRE} → {@code A_FAIRE}</li>
 *   <li>{@code EN_COURS} → {@code EN_COURS}</li>
 *   <li>{@code TERMINEE} → {@code TERMINE} (Oracle n'utilise pas le "E" final)</li>
 * </ul>
 *
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatutUpdateRequest {

    /**
     * Nouveau statut de la tâche envoyé par Angular.
     * Valeurs acceptées : A_FAIRE, EN_COURS, TERMINEE.
     * La conversion vers les valeurs Oracle est effectuée dans {@code TacheService.toOracleStatut()}.
     */
    @NotBlank(message = "Le statut est obligatoire")
    private String statut;

    /**
     * Pourcentage de progression de la tâche (0-100), optionnel.
     * Mis à jour simultanément avec le statut lors du drag &amp; drop Kanban.
     * Si absent, la progression est calculée automatiquement selon le statut :
     * A_FAIRE → 0, TERMINE → 100, autres → valeur inchangée.
     */
    private Integer progression;
}