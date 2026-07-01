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
 * Contrôleur REST pour l'espace employé — consultation des projets et tâches personnels.
 * <p>
 * Expose les endpoints {@code /api/projets} permettant à un employé de :
 * <ul>
 *   <li>Consulter ses projets ({@code GET /api/projets}).</li>
 *   <li>Voir le détail d'un projet auquel il participe ({@code GET /api/projets/{id}}).</li>
 *   <li>Lister ses tâches assignées ({@code GET /api/projets/mes-taches}).</li>
 *   <li>Basculer le statut d'une tâche ({@code PATCH /api/projets/taches/{id}/toggle}).</li>
 *   <li>Mettre à jour le pourcentage d'avancement d'une tâche ({@code PUT /api/projets/taches/{id}/avancement}).</li>
 * </ul>
 * </p>
 * <p>
 * Expose également {@code GET /api/projets/by-name} consommé par {@code taches-service}
 * via Feign (ProjetClient.findByName()) pour résoudre un nom de projet en DTO sans accès JPA direct.
 * </p>
 * <p>
 * {@code @Slf4j} : journalisation SLF4J via Lombok.
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/projets")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
class EmployeProjetController {

    /** Service métier centralisant la logique projets. */
    private final ProjetService projetService;

    /**
     * Retourne les projets auxquels l'employé authentifié participe.
     *
     * @param auth contexte d'authentification (employé identifié depuis le JWT)
     * @return liste des projets (HTTP 200), liste vide en cas d'erreur technique
     */
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

    /**
     * Retourne le détail d'un projet spécifique accessible à l'employé authentifié.
     *
     * @param id   identifiant du projet
     * @param auth contexte d'authentification pour le contrôle d'accès
     * @return le DTO du projet (HTTP 200), HTTP 404 si introuvable, HTTP 403 si accès interdit
     */
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

    /**
     * Retourne les tâches assignées à l'employé authentifié.
     *
     * @param auth contexte d'authentification
     * @return liste des tâches de l'employé (HTTP 200)
     */
    @GetMapping("/mes-taches")
    @PreAuthorize("hasRole('EMPLOYE')")
    public ResponseEntity<List<TacheDTO>> getMesTaches(Authentication auth) {
        return ResponseEntity.ok(projetService.getMesTaches(auth));
    }

    /**
     * Bascule le statut d'une tâche entre {@code EN_COURS} et {@code TERMINEE}.
     *
     * @param id   identifiant de la tâche
     * @param auth contexte d'authentification
     * @return la tâche avec le nouveau statut (HTTP 200)
     */
    @PatchMapping("/taches/{id}/toggle")
    @PreAuthorize("hasRole('EMPLOYE')")
    public ResponseEntity<TacheDTO> toggleTache(
            @PathVariable Long id,
            Authentication auth) {
        return ResponseEntity.ok(projetService.toggleTache(id, auth));
    }

    /**
     * Met à jour le pourcentage d'avancement d'une tâche.
     *
     * @param id   identifiant de la tâche
     * @param body map JSON contenant la clé {@code progressPct} (0-100)
     * @param auth contexte d'authentification
     * @return la tâche avec le nouveau pourcentage d'avancement (HTTP 200)
     */
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