package com.gerai.analyticsservice.dto;

import lombok.*;
import java.util.Map;

/**
 * Stats des formations — retourné par GET /api/analytics/formations
 *
 * Source : table TRAINING_REQUESTS + V_ALL_DEMANDES.
 *
 * CORRECTION : les commentaires indiquaient 'VALIDE_RH' et 'REJETE'
 * alors que les statuts réels de TRAINING_REQUESTS sont :
 *   EN_ATTENTE | APPROUVE_CHEF | APPROUVE_RH | REFUSE | ANNULE
 *
 * CORRECTION : ajout de tauxRejet (cohérence avec CongeStatsDTO et StatsService).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormationStatsDTO {

    /* ── Comptages ───────────────────────────────────── */

    /** Nombre total de demandes de formation. */
    private long totalFormations;

    /** Nombre de formations approuvées par le RH (statut = APPROUVE_RH). */
    private long formationsValidees;

    /** Nombre de formations refusées (statut = REFUSE). */
    private long formationsRefusees;

    /** Nombre de formations en attente de validation (statut = EN_ATTENTE). */
    private long formationsEnAttente;

    /* ── Indicateurs financiers / durée ──────────────── */

    /** Budget total alloué aux formations approuvées (SUM(estimated_cost) pour APPROUVE_RH). */
    private double budgetTotal;

    /** Durée moyenne des formations en jours (AVG de duration_days). */
    private double moyenneDureeJours;

    /* ── Répartitions ────────────────────────────────── */

    /** Répartition mensuelle des formations, clé au format "YYYY-MM" (ex : 2026-01:2). */
    private Map<String, Long> formationsParMois;

    /** Répartition par type de formation (ex : TECHNIQUE:5). */
    private Map<String, Long> formationsParType;

    /* ── Taux ────────────────────────────────────────── */

    /** Taux de validation des formations en pourcentage (formationsValidees / total * 100). */
    private double tauxValidation;

    /** Taux de rejet des formations en pourcentage (formationsRefusees / total * 100). */
    private double tauxRejet;
}