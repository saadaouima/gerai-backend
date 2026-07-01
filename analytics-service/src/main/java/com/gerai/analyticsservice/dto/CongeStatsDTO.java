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

    /** Nombre total de demandes de congé toutes périodes confondues. */
    private long totalConges;

    /** Nombre de congés validés par le RH (statut = VALIDE_RH). */
    private long congesValides;

    /** Nombre de congés refusés (statut = REFUSE, non REJETE). */
    private long congesRefuses;

    /** Nombre de congés en attente de validation (statut = EN_ATTENTE). */
    private long congesEnAttente;

    /* ── Indicateurs ─────────────────────────────────── */

    /** Durée moyenne des congés en jours (AVG de DAYS_COUNT). */
    private double moyenneJours;

    /** Taux d'acceptation des congés en pourcentage (congesValides / total * 100). */
    private double tauxAcceptation;

    /** Taux de rejet des congés en pourcentage (congesRefuses / total * 100). */
    private double tauxRejet;

    /* ── Répartitions temporelles ────────────────────── */

    /** Répartition mensuelle des congés, clé au format "YYYY-MM" (ex : 2026-01:3). */
    private Map<String, Long> congesParMois;

    /** Répartition par type de congé (ex : CONGE_ANNUEL:10). */
    private Map<String, Long> congesParType;
}