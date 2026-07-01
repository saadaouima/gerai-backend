package com.gerai.analyticsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de statistiques d'attrition pour un département.
 * Utilisé dans {@link AttritionSummaryDTO} pour présenter le risque
 * agrégé par unité organisationnelle.
 *
 * @since 1.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeptAttritionStatDTO {

    /** Nom du département tel qu'il apparaît dans la table DEPARTMENTS. */
    private String departement;

    /** Score moyen d'attrition des employés du département (arrondi à 1 décimale). */
    private double avgScore;

    /** Nombre d'employés du département en risque ÉLEVÉ (score ≥ 70). */
    private long   risqueEleve;

    /** Nombre total d'employés actifs analysés dans ce département. */
    private long   total;
}
