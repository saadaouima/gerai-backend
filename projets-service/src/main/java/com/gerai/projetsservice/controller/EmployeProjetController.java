package com.gerai.projetsservice.controller;

import com.gerai.projetsservice.dto.*;
import com.gerai.projetsservice.service.ProjetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.http.HttpStatus;

/**
 * Endpoints espace Employé (/api/projets) + endpoint partagé (/api/projets/by-name).
 *
 * CORRECTION : ajout de GET /api/projets/by-name appelé par taches-service via Feign.
 * Sans cet endpoint, ProjetClient.findByName() échoue avec 404.
 */
@Slf4j
@RestController
@RequestMapping("/api/projets")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
class EmployeProjetController {

    private final ProjetService projetService;

    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<ProjetDTO>> getMesProjets(Authentication auth) {
        try {
            return ResponseEntity.ok(projetService.getMesProjets(auth));
        } catch (Exception e) {
            log.error("getMesProjets failed: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<?> getProjetById(
            @PathVariable Long id,
            Authentication auth) {
        try {
            return ResponseEntity.ok(projetService.getProjetById(id, auth));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Projet introuvable"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Accès interdit à ce projet"));
        } catch (Exception e) {
            log.error("getProjetById failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/mes-taches")
    @PreAuthorize("hasRole('EMPLOYE')")
    public ResponseEntity<List<TacheDTO>> getMesTaches(Authentication auth) {
        return ResponseEntity.ok(projetService.getMesTaches(auth));
    }

    @PatchMapping("/taches/{id}/toggle")
    @PreAuthorize("hasRole('EMPLOYE')")
    public ResponseEntity<TacheDTO> toggleTache(
            @PathVariable Long id,
            Authentication auth) {
        return ResponseEntity.ok(projetService.toggleTache(id, auth));
    }

    @PutMapping("/taches/{id}/avancement")
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF')")
    public ResponseEntity<TacheDTO> updateAvancement(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body,
            Authentication auth) {
        return ResponseEntity.ok(
                projetService.updateAvancement(id, body.get("progressPct"), auth));
    }

    /**
     * GET /api/projets/by-name?nom={nom}
     *
     * Consommé par taches-service via Feign (ProjetClient.findByName()).
     * Permet à taches-service de résoudre un nom de projet en ProjetDTO
     * sans avoir de JPA sur la table PROJECTS.
     *
     * Accessible à tous les rôles authentifiés car taches-service
     * propage son propre JWT portant le rôle CHEF ou EMPLOYE.
     */
    @GetMapping("/by-name")
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Optional<ProjetDTO>> findByName(
            @RequestParam String nom,
            Authentication auth) {
        log.info("[Projets] GET /by-name | nom='{}' | caller={}", nom, auth.getName());
        return ResponseEntity.ok(projetService.findByName(nom, auth));
    }
}