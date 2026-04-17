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
    private long totalDemandes;
    private long demandesEnAttente;
    private long demandesValidees;    // somme de tous statuts d'approbation finale
    private long demandesRejetees;    // statut = REFUSE
    private long demandesAnnulees;    // statut = ANNULE

    /* ── Répartitions ────────────────────────────────── */
    private Map<String, Long> demandesParType;    // CONGE:12, PRET:5 ...
    private Map<String, Long> demandesParStatut;  // EN_ATTENTE:3 ...
    private Map<String, Long> demandesParMois;    // 2026-01:8 ...

    /* ── Taux (en %) ─────────────────────────────────── */
    private double tauxAcceptation;
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
    private CongeStatsDTO     congeStats;
    private FormationStatsDTO formationStats;
}