package com.gerai.projetsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO des métriques de performance agrégées d'un chef de projet.
 * <p>
 * Retourné par {@code GET /api/chef/performance}.
 * Les valeurs sont exprimées en pourcentage (0-100).
 * </p>
 *
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceChefDTO {
    /** Taux de livraison des projets dans les délais (projets terminés / total × 100). */
    private double tauxLivraisonProjet;
    /** Score de satisfaction client estimé (basé sur l'avancement et les évaluations). */
    private double satisfactionClient;
    /** Taux de collaboration d'équipe (tâches terminées / total × 100). */
    private double collaborationEquipe;
    /** Score de qualité de code (basé sur les évaluations de performance soumises). */
    private double qualiteCode;
    /** Temps moyen de résolution des bugs (indicateur calculé). */
    private double tempsResolutionBugs;
}
