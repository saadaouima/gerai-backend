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
    @PreAuthorize("hasAnyRole('RH','CHEF','ADMIN')")
    public ResponseEntity<DashboardSummaryDTO> getDashboard(Authentication auth) {
        return ResponseEntity.ok(statsService.getDashboard(auth));
    }

    /* ── Congés ───────────────────────────────────────── */

    @GetMapping("/conges")
    @PreAuthorize("hasAnyRole('RH','CHEF','ADMIN')")
    public ResponseEntity<CongeStatsDTO> getCongeStats() {
        return ResponseEntity.ok(statsService.getCongeStats());
    }

    /* ── Formations ───────────────────────────────────── */

    @GetMapping("/formations")
    @PreAuthorize("hasAnyRole('RH','CHEF','ADMIN')")
    public ResponseEntity<FormationStatsDTO> getFormationStats() {
        return ResponseEntity.ok(statsService.getFormationStats());
    }

    /* ── Par mois + type ──────────────────────────────── */

    @GetMapping("/par-mois")
    @PreAuthorize("hasAnyRole('RH','CHEF','ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getParMois() {
        return ResponseEntity.ok(statsService.getDemandesParMoisEtType());
    }

    /* ── Fiche employé ────────────────────────────────── */

    /**
     * NOUR (EMPLOYE) passe son propre employeeId.
     * RH / CHEF peuvent consulter n'importe quel employé.
     */
    @GetMapping("/employe/{employeId}")
    @PreAuthorize("hasAnyRole('RH','CHEF','EMPLOYE','ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getDemandesEmploye(
            @PathVariable Long employeId) {
        return ResponseEntity.ok(statsService.getDemandesEmploye(employeId));
    }

    /* ── Stats par département (RH uniquement) ────────── */

    @GetMapping("/par-departement")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getParDepartement() {
        return ResponseEntity.ok(statsService.getStatsParDepartement());
    }

    /* ── Top 5 absences ───────────────────────────────── */

    /**
     * RH → top 5 global.
     * CHEF → top 5 de son département (résolution automatique dans StatsService).
     */
    @GetMapping("/top-absences")
    @PreAuthorize("hasAnyRole('RH','CHEF','ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getTopAbsences(Authentication auth) {
        return ResponseEntity.ok(statsService.getTop5EmployesAbsences(auth));
    }

    /* ── Export JSON brut (debug / Power BI) ─────────── */

    /**
     * deptId optionnel : ignoré pour les Chefs (StatsService applique leur vrai dept).
     * Utile pour le RH qui veut filtrer sur un département précis.
     */
    @GetMapping("/report/conges")
    @PreAuthorize("hasAnyRole('RH','CHEF','ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> exportConges(
            Authentication auth,
            @RequestParam(required = false) Long deptId) {
        return ResponseEntity.ok(statsService.getCongesForReport(deptId, auth));
    }

    @GetMapping("/report/formations")
    @PreAuthorize("hasAnyRole('RH','CHEF','ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> exportFormations(
            Authentication auth,
            @RequestParam(required = false) Long deptId) {
        return ResponseEntity.ok(statsService.getFormationsForReport(deptId, auth));
    }
}