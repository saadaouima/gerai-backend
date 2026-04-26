package com.gerai.analyticsservice.dto;

import lombok.*;
import java.util.Map;

/**
 * Stats des congés — retourné par GET /api/analytics/conges
 *
 * Source : table LEAVE_REQUESTS + V_ALL_DEMANDES.
 *
 * CORRECTION : le commentaire du champ congesRefuses indiquait 'REJETE'
 * alors que le statut réel dans LEAVE_REQUESTS est 'REFUSE'
 * (contrainte CHECK dans V3__hr_requests.sql).
 *
 * CORRECTION : ajout de tauxRejet qui était présent dans StatsService
 * mais absent du DTO précédent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CongeStatsDTO {

    /* ── Comptages ───────────────────────────────────── */
    private long totalConges;
    private long congesValides;       // statut = VALIDE_RH
    private long congesRefuses;       // statut = REFUSE  (pas REJETE)
    private long congesEnAttente;     // statut = EN_ATTENTE

    /* ── Indicateurs ─────────────────────────────────── */
    private double moyenneJours;      // AVG(days_count)
    private double tauxAcceptation;   // congesValides / total * 100
    private double tauxRejet;         // congesRefuses / total * 100

    /* ── Répartitions temporelles ────────────────────── */
    private Map<String, Long> congesParMois;  // 2026-01:3 ...
    private Map<String, Long> congesParType;  // CONGE_ANNUEL:10 ...
}