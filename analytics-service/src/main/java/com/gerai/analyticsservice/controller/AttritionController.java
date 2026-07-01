package com.gerai.analyticsservice.controller;

import com.gerai.analyticsservice.dto.AttritionPredictionDTO;
import com.gerai.analyticsservice.dto.AttritionSummaryDTO;
import com.gerai.analyticsservice.service.AttritionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints for the attrition prediction module.
 * Accessible only to ADMIN and RH roles.
 *
 *   GET  /api/admin/attrition/summary           → global aggregates
 *   GET  /api/admin/attrition/predictions       → per-employee risk list
 *   GET  /api/admin/attrition/predictions/{id}  → single employee risk
 *   POST /api/admin/attrition/refresh           → evict caches and recompute
 */
@RestController
@RequestMapping("/api/admin/attrition")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class AttritionController {

    private final AttritionService attritionService;

    /**
     * Retourne le résumé agrégé des risques d'attrition pour tous les employés actifs.
     * Inclut les totaux par niveau de risque (ÉLEVÉ, MOYEN, FAIBLE) et les statistiques
     * par département.
     *
     * @return un {@link AttritionSummaryDTO} avec les indicateurs globaux d'attrition
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','ADMIN_RH', 'RH')")
    public ResponseEntity<AttritionSummaryDTO> getSummary() {
        return ResponseEntity.ok(attritionService.getSummary());
    }

    /**
     * Retourne la liste complète des prédictions d'attrition pour chaque employé actif.
     * Chaque entrée contient le score de risque (0–100), le niveau, les facteurs
     * contributeurs et les recommandations RH.
     *
     * @return la liste des {@link AttritionPredictionDTO} triés par identifiant employé
     */
    @GetMapping("/predictions")
    @PreAuthorize("hasAnyRole('ADMIN','ADMIN_RH', 'RH')")
    public ResponseEntity<List<AttritionPredictionDTO>> getPredictions() {
        return ResponseEntity.ok(attritionService.getPredictions());
    }

    /**
     * Retourne la prédiction d'attrition pour un employé précis.
     *
     * @param employeId l'identifiant Oracle de l'employé (EMPLOYEES.employee_id)
     * @return la prédiction de l'employé, ou HTTP 404 si l'employé n'est pas trouvé
     */
    @GetMapping("/predictions/{employeId}")
    @PreAuthorize("hasAnyRole('ADMIN','ADMIN_RH', 'RH')")
    public ResponseEntity<AttritionPredictionDTO> getPrediction(@PathVariable Long employeId) {
        return attritionService.getPredictions().stream()
                .filter(p -> p.getEmployeId().equals(employeId))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Invalide les caches d'attrition et force le recalcul des prédictions
     * au prochain appel. Utile après une mise à jour significative des données RH.
     *
     * @return HTTP 200 si l'invalidation a réussi
     */
    @PostMapping("/refresh")
    @PreAuthorize("hasAnyRole('ADMIN','ADMIN_RH', 'RH')")
    public ResponseEntity<Void> refresh() {
        attritionService.refresh();
        return ResponseEntity.ok().build();
    }
}
