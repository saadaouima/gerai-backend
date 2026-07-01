package com.gerai.analyticsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO de prédiction d'attrition pour un employé.
 * Contient le score de risque calculé sur 4 facteurs (chacun noté sur 25 points),
 * le niveau de risque associé et les recommandations RH générées automatiquement.
 *
 * Seuils de niveau de risque :
 * <ul>
 *   <li>score &lt; 40 → FAIBLE</li>
 *   <li>40 ≤ score &lt; 70 → MOYEN</li>
 *   <li>score ≥ 70 → ÉLEVÉ</li>
 * </ul>
 *
 * @since 1.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AttritionPredictionDTO {

    /** Identifiant Oracle de l'employé (EMPLOYEES.employee_id). */
    private Long   employeId;

    /** Nom de famille de l'employé. */
    private String nom;

    /** Prénom de l'employé. */
    private String prenom;

    /** URL de la photo de profil de l'employé (nullable). */
    private String photo;

    /** Nom du département auquel l'employé est rattaché. */
    private String departement;

    /** Intitulé du poste occupé par l'employé. */
    private String poste;

    /** Score de risque d'attrition global, compris entre 0 et 100. */
    private int    riskScore;

    /** Niveau de risque : ELEVE (≥70), MOYEN (40-69), FAIBLE (&lt;40). */
    private String riskLevel;

    /** Liste des facteurs de risque ayant contribué au score (impact > 0). */
    private List<RiskFactorDTO>  facteurs;

    /** Recommandations RH générées automatiquement selon les facteurs identifiés. */
    private List<String>         recommandations;

    /** Horodatage du dernier calcul au format "dd/MM/yyyy HH:mm". */
    private String lastUpdated;
}
