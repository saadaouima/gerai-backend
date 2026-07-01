package com.gerai.analyticsservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.util.Map;

/**
 * DTO principal retourné par GET /api/analytics/dashboard
 *
 * Tous les champs correspondent exactement à ce que StatsService.getDashboard()
 * renseigne dans le builder.
 *
 * CORRECTION : ajout de @JsonInclude(NON_NULL) pour éviter que les champs
 * congeStats et formationStats (optionnels, non renseignés par getDashboard())
 * apparaissent comme null dans le JSON Angular.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardSummaryDTO {

    /* ── Comptages demandes ───────────────────────────── */

    /** Nombre total de demandes RH (toutes tables confondues). */
    private long totalDemandes;

    /** Nombre de demandes dont le statut est EN_ATTENTE. */
    private long demandesEnAttente;

    /** Nombre de demandes validées (somme de tous les statuts d'approbation finale). */
    private long demandesValidees;

    /** Nombre de demandes refusées (statut = REFUSE). */
    private long demandesRejetees;

    /** Nombre de demandes annulées (statut = ANNULE). */
    private long demandesAnnulees;

    /* ── Répartitions ────────────────────────────────── */

    /** Répartition des demandes par type (ex : CONGE:12, PRET:5). */
    private Map<String, Long> demandesParType;

    /** Répartition des demandes par statut (ex : EN_ATTENTE:3). */
    private Map<String, Long> demandesParStatut;

    /** Répartition mensuelle des demandes, clé au format "YYYY-MM" (ex : 2026-01:8). */
    private Map<String, Long> demandesParMois;

    /* ── Taux (en %) ─────────────────────────────────── */

    /** Taux d'acceptation global des demandes en pourcentage. */
    private double tauxAcceptation;

    /** Taux de rejet global des demandes en pourcentage. */
    private double tauxRejet;

    /* ── KPIs temps réel ─────────────────────────────── */
    /** Nombre d'employés en congé validé aujourd'hui */
    private long   absentsAujourdhui;

    /** Taux d'absentéisme du mois courant (en %) */
    private double tauxAbsenteismeMoisCourant;

    /** Nombre de projets en statut EN_COURS */
    private long   projetsActifs;

    /** Nombre de tâches non terminées (tous statuts sauf TERMINE) */
    private long   tachesOuvertes;

    /* ── Sous-stats intégrées (optionnelles) ─────────── */

    /** Statistiques détaillées des congés (optionnel, null si non demandé). */
    private CongeStatsDTO     congeStats;

    /** Statistiques détaillées des formations (optionnel, null si non demandé). */
    private FormationStatsDTO formationStats;
}