package com.gerai.analyticsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO représentant un facteur de risque contribuant au score d'attrition d'un employé.
 * Chaque facteur est noté sur 25 points ; le score total est la somme des 4 facteurs
 * (ancienneté, absences, taux de refus, engagement projet).
 *
 * @since 1.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RiskFactorDTO {

    /** Libellé descriptif du facteur de risque (ex : "Absences élevées (≥ 30 j/an)"). */
    private String label;

    /** Points contribués au score de risque global, compris entre 0 et 25. */
    private int    impact;

    /** Classe CSS de l'icône Tabler à afficher dans l'interface Angular (ex : "ti ti-calendar-off"). */
    private String icon;
}
