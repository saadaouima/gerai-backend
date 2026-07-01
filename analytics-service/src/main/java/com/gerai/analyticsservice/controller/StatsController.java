package com.gerai.analyticsservice.controller;

import com.gerai.analyticsservice.dto.*;
import com.gerai.analyticsservice.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoints REST des statistiques analytiques.
 *
 * Règles d'accès :
 *   IMED (ADMIN/RH)      → /dashboard, /par-departement, /top-absences, rapports globaux
 *   MARIEM / AMAL (CHEF) → /dashboard (filtré), /conges, /formations, rapports filtrés
 *   NOUR (EMPLOYE)       → /employe/{id} uniquement
 *
 * Le filtrage par département est entièrement géré par StatsService
 * à partir du JWT — l'URL ne contient PAS le deptId pour les Chefs.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class StatsController {

    private final StatsService statsService;

    /* ── Dashboard ─────────────────────────────────────── */

    /**
     * IMED (ADMIN/RH) → dashboard global toutes données.
     * MARIEM / AMAL (CHEF) → dashboard filtré sur leur département.
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('RH','CHEF','ADMIN','ADMIN_RH')")
    public ResponseEntity<DashboardSummaryDTO> getDashboard(Authentication auth) {
        return ResponseEntity.ok(statsService.getDashboard(auth));
    }

    /* ── Congés ───────────────────────────────────────── */

    /**
     * Retourne les statistiques globales des congés :
     * comptages par statut, moyenne de jours, taux d'acceptation et réjection.
     *
     * @return un {@link CongeStatsDTO} avec les indicateurs des congés
     */
    @GetMapping("/conges")
    @PreAuthorize("hasAnyRole('RH','CHEF','ADMIN','ADMIN_RH')")
    public ResponseEntity<CongeStatsDTO> getCongeStats() {
        return ResponseEntity.ok(statsService.getCongeStats());
    }

    /* ── Formations ───────────────────────────────────── */

    /**
     * Retourne les statistiques globales des formations RH :
     * comptages, budget total validé, durée moyenne et taux de validation.
     *
     * @return un {@link FormationStatsDTO} avec les indicateurs des formations
     */
    @GetMapping("/formations")
    @PreAuthorize("hasAnyRole('RH','CHEF','ADMIN','ADMIN_RH')")
    public ResponseEntity<FormationStatsDTO> getFormationStats() {
        return ResponseEntity.ok(statsService.getFormationStats());
    }

    /* ── Par mois + type ──────────────────────────────── */

    /**
     * Retourne la liste des demandes groupées par mois et par type.
     * Utile pour alimenter les graphiques de tendances temporelles dans Angular.
     *
     * @return une liste de maps avec les clés "mois" (YYYY-MM), "type" et "total"
     */
    @GetMapping("/par-mois")
    @PreAuthorize("hasAnyRole('RH','CHEF','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<Map<String, Object>>> getParMois() {
        return ResponseEntity.ok(statsService.getDemandesParMoisEtType());
    }

    /* ── Fiche employé ────────────────────────────────── */

    /**
     * NOUR (EMPLOYE) passe son propre employeeId.
     * RH / CHEF peuvent consulter n'importe quel employé.
     */
    @GetMapping("/employe/{employeId}")
    @PreAuthorize("hasAnyRole('RH','CHEF','EMPLOYE','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<Map<String, Object>>> getDemandesEmploye(
            @PathVariable Long employeId) {
        return ResponseEntity.ok(statsService.getDemandesEmploye(employeId));
    }

    /* ── Stats par département (RH uniquement) ────────── */

    /**
     * Retourne les statistiques agrégées par département (effectif, congés,
     * formations, projets actifs). Accessible uniquement aux rôles RH et ADMIN.
     *
     * @return une liste de maps par département avec les colonnes
     *         DEPT_NAME, HEADCOUNT, NB_CONGES, NB_FORMATIONS, NB_PROJETS
     */
    @GetMapping("/par-departement")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<Map<String, Object>>> getParDepartement() {
        return ResponseEntity.ok(statsService.getStatsParDepartement());
    }

    /* ── Top 5 absences ───────────────────────────────── */

    /**
     * RH → top 5 global.
     * CHEF → top 5 de son département (résolution automatique dans StatsService).
     */
    @GetMapping("/top-absences")
    @PreAuthorize("hasAnyRole('RH','CHEF','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<Map<String, Object>>> getTopAbsences(Authentication auth) {
        return ResponseEntity.ok(statsService.getTop5EmployesAbsences(auth));
    }

    /* ── Export JSON brut (debug / Power BI) ─────────── */

    /**
     * Retourne les données brutes des congés au format JSON pour intégration
     * externe (Power BI, debug). Le filtre {@code deptId} est ignoré pour les Chefs ;
     * c'est toujours leur propre département qui est appliqué.
     *
     * @param auth   le contexte d'authentification pour la résolution du rôle
     * @param deptId identifiant de département optionnel (pour RH/ADMIN uniquement)
     * @return la liste des demandes de congé avec toutes les colonnes du rapport
     */
    @GetMapping("/report/conges")
    @PreAuthorize("hasAnyRole('RH','CHEF','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<Map<String, Object>>> exportConges(
            Authentication auth,
            @RequestParam(required = false) Long deptId) {
        return ResponseEntity.ok(statsService.getCongesForReport(deptId, auth));
    }

    /**
     * Retourne les données brutes des formations au format JSON pour intégration
     * externe (Power BI, debug). Le filtre {@code deptId} est ignoré pour les Chefs ;
     * c'est toujours leur propre département qui est appliqué.
     *
     * @param auth   le contexte d'authentification pour la résolution du rôle
     * @param deptId identifiant de département optionnel (pour RH/ADMIN uniquement)
     * @return la liste des demandes de formation avec toutes les colonnes du rapport
     */
    @GetMapping("/report/formations")
    @PreAuthorize("hasAnyRole('RH','CHEF','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<Map<String, Object>>> exportFormations(
            Authentication auth,
            @RequestParam(required = false) Long deptId) {
        return ResponseEntity.ok(statsService.getFormationsForReport(deptId, auth));
    }
}