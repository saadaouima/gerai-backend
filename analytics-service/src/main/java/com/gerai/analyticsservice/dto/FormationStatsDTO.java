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
    private long totalFormations;
    private long formationsValidees;   // statut = APPROUVE_RH  (pas VALIDE_RH)
    private long formationsRefusees;   // statut = REFUSE        (pas REJETE)
    private long formationsEnAttente;  // statut = EN_ATTENTE

    /* ── Indicateurs financiers / durée ──────────────── */
    private double budgetTotal;        // SUM(estimated_cost) des APPROUVE_RH
    private double moyenneDureeJours;  // AVG(duration_days)

    /* ── Répartitions ────────────────────────────────── */
    private Map<String, Long> formationsParMois;  // 2026-01:2 ...
    private Map<String, Long> formationsParType;  // TECHNIQUE:5 ...

    /* ── Taux ────────────────────────────────────────── */
    private double tauxValidation;   // formationsValidees / total * 100
    private double tauxRejet;        // formationsRefusees / total * 100
}