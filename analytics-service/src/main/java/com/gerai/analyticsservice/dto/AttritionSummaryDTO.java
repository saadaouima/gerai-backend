package com.gerai.analyticsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO de synthèse des risques d'attrition pour l'ensemble des employés actifs.
 * Agrège les comptages par niveau de risque et fournit les statistiques
 * par département pour l'affichage dans le tableau de bord attrition.
 *
 * @since 1.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AttritionSummaryDTO {

    /** Nombre total d'employés actifs analysés. */
    private long   totalEmployes;

    /** Nombre d'employés en risque d'attrition ÉLEVÉ (score ≥ 70). */
    private long   risqueEleve;

    /** Nombre d'employés en risque d'attrition MOYEN (40 ≤ score &lt; 70). */
    private long   risqueMoyen;

    /** Nombre d'employés en risque d'attrition FAIBLE (score &lt; 40). */
    private long   risqueFaible;

    /** Score moyen d'attrition de tous les employés actifs (arrondi à 1 décimale). */
    private double avgScore;

    /** Statistiques d'attrition agrégées par département, triées par score moyen décroissant. */
    private List<DeptAttritionStatDTO> deptStats;
}
