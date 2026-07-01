package com.gerai.projetsservice.controller;

import com.gerai.projetsservice.dto.*;
import com.gerai.projetsservice.service.ProjetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
/**
 * Contrôleur REST pour l'espace administration RH.
 * <p>
 * Expose les endpoints {@code /api/admin} permettant aux rôles RH/ADMIN/ADMIN_RH de :
 * <ul>
 *   <li>Consulter l'ensemble des projets ({@code GET /api/admin/projets}).</li>
 *   <li>Obtenir les statistiques détaillées d'un projet ({@code GET /api/admin/projets/{id}/stats}).</li>
 *   <li>Lister et créer des évaluations de performance ({@code GET/POST /api/admin/evals}).</li>
 *   <li>Obtenir le tableau de bord global ({@code GET /api/admin/dashboard}).</li>
 * </ul>
 * </p>
 * <p>
 * {@code @PreAuthorize} : contrôle d'accès fin par méthode basé sur les rôles Keycloak.
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class AdminProjetController {

    private final ProjetService projetService;

    /**
     * Retourne tous les projets de la plateforme (vision globale admin).
     * <p>
     * En cas d'erreur technique, retourne une liste vide (HTTP 200) pour ne pas bloquer le tableau de bord.
     * </p>
     *
     * @return liste de tous les projets enrichis (HTTP 200)
     */
    @GetMapping("/projets")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<ProjetDTO>> getAllProjets() {
        try {
            return ResponseEntity.ok(projetService.getAllProjets());
        } catch (Exception e) {
            log.error("getAllProjets failed: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Retourne les statistiques détaillées d'un projet spécifique.
     *
     * @param id   identifiant du projet
     * @param auth contexte d'authentification pour les contrôles d'accès
     * @return le DTO du projet avec membres et tâches (HTTP 200)
     */
    @GetMapping("/projets/{id}/stats")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<ProjetDTO> getProjetStats(
            @PathVariable Long id,
            Authentication auth) {
        return ResponseEntity.ok(projetService.getProjetById(id, auth));
    }

    /**
     * Retourne toutes les évaluations de performance.
     *
     * @return liste complète des évaluations (HTTP 200)
     */
    @GetMapping("/evals")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<PerformanceEvalDTO>> getEvals() {
        return ResponseEntity.ok(projetService.getEvals());
    }

    /**
     * Crée une nouvelle évaluation de performance.
     *
     * @param req  données de l'évaluation (employé évalué, période, score...)
     * @param auth contexte d'authentification (l'évaluateur est déduit du JWT)
     * @return l'évaluation créée (HTTP 201)
     */
    @PostMapping("/evals")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH','CHEF')")
    public ResponseEntity<PerformanceEvalDTO> createEval(
            @RequestBody PerformanceEvalDTO req,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projetService.createEval(req, auth));
    }

    /**
     * Retourne les statistiques agrégées du tableau de bord administrateur.
     * <p>
     * Inclut : nombre total de projets, projets en cours/terminés/en attente/en pause,
     * nombre de tâches total, tâches terminées et taux de complétion global.
     * </p>
     *
     * @return données consolidées du tableau de bord (HTTP 200)
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<DashboardDTO> getDashboard() {
        return ResponseEntity.ok(projetService.getDashboard());
    }
}
