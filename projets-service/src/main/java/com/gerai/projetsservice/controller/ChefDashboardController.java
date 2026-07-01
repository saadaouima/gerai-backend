package com.gerai.projetsservice.controller;

import com.gerai.projetsservice.dto.PerformanceChefDTO;
import com.gerai.projetsservice.service.ProjetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur REST pour le tableau de bord de performance du chef de projet.
 * <p>
 * Expose l'endpoint {@code GET /api/chef/performance} retournant des métriques
 * calculées pour le chef authentifié : taux de livraison des projets, satisfaction
 * client estimée, collaboration d'équipe, qualité du code et temps de résolution des bugs.
 * </p>
 * <p>
 * En cas d'erreur, retourne des valeurs à zéro pour ne pas bloquer l'affichage du tableau de bord.
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/chef")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
class ChefDashboardController {

    private final ProjetService projetService;

    /**
     * Retourne les métriques de performance agrégées du chef de projet authentifié.
     * <p>
     * Calcule le taux de livraison (projets terminés/total), la collaboration d'équipe
     * (tâches terminées/total), et un score qualité basé sur les évaluations soumises.
     * </p>
     *
     * @param auth contexte d'authentification (chef identifié depuis le JWT)
     * @return le DTO de performance avec toutes les métriques (HTTP 200)
     */
    @GetMapping("/performance")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<PerformanceChefDTO> getPerformance(Authentication auth) {
        try {
            return ResponseEntity.ok(projetService.getPerformanceChef(auth));
        } catch (Exception e) {
            log.error("getPerformance failed: {}", e.getMessage());
            return ResponseEntity.ok(PerformanceChefDTO.builder()
                    .tauxLivraisonProjet(0).satisfactionClient(0)
                    .collaborationEquipe(0).qualiteCode(0).tempsResolutionBugs(0)
                    .build());
        }
    }
}
