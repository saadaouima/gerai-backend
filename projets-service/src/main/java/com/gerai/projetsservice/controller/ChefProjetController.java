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
import java.util.NoSuchElementException;

/**
 * Contrôleur REST pour la gestion des projets et tâches par le chef de projet.
 * <p>
 * Expose les endpoints {@code /api/affectation} permettant au chef de :
 * <ul>
 *   <li>Consulter ses projets et les détails d'un projet spécifique.</li>
 *   <li>Créer, modifier et supprimer des projets.</li>
 *   <li>Ajouter des membres à un projet.</li>
 *   <li>Créer, modifier, assigner et supprimer des tâches.</li>
 *   <li>Consulter les tâches de tous ses projets.</li>
 *   <li>Consulter la liste des employés disponibles pour affectation.</li>
 * </ul>
 * </p>
 * <p>
 * Les rôles RH/ADMIN ont également accès pour la supervision globale.
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/affectation")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
class ChefProjetController {

    private final ProjetService projetService;

    /**
     * Retourne les projets du chef authentifié (ou tous les projets pour admin/RH).
     *
     * @param auth contexte d'authentification
     * @return liste des projets (HTTP 200), liste vide en cas d'erreur technique
     */
    @GetMapping("/projets")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<List<ProjetDTO>> getProjets(Authentication auth) {
        try {
            return ResponseEntity.ok(projetService.getProjetsChef(auth));
        } catch (Exception e) {
            log.error("getProjets failed: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Retourne le détail d'un projet spécifique (membres, tâches, statistiques).
     *
     * @param id   identifiant du projet
     * @param auth contexte d'authentification pour le contrôle d'accès
     * @return le DTO du projet (HTTP 200), HTTP 404 si introuvable, HTTP 403 si accès interdit
     */
    @GetMapping("/projets/{id}")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
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
     * Retourne toutes les tâches de tous les projets du chef authentifié.
     *
     * @param auth contexte d'authentification
     * @return liste des tâches (HTTP 200), liste vide en cas d'erreur technique
     */
    @GetMapping("/taches")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<List<TacheDTO>> getTaches(Authentication auth) {
        try {
            return ResponseEntity.ok(projetService.getTachesChef(auth));
        } catch (Exception e) {
            log.error("getTaches failed: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Crée un nouveau projet et affecte le chef authentifié comme chef de projet.
     *
     * @param req  données de création du projet (nom, dates, description...)
     * @param auth contexte d'authentification (chef identifié depuis le JWT)
     * @return le projet créé (HTTP 201), HTTP 400 si données invalides, HTTP 500 si erreur serveur
     */
    @PostMapping("/projets")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<?> createProjet(
            @Valid @RequestBody CreateProjetRequest req,
            Authentication auth) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(projetService.createProjet(req, auth));
        } catch (IllegalArgumentException e) {
            log.warn("createProjet rejected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("createProjet failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Met à jour les informations d'un projet existant.
     *
     * @param id   identifiant du projet à modifier
     * @param req  nouvelles données (seuls les champs non nuls sont appliqués)
     * @param auth contexte d'authentification pour le contrôle d'accès
     * @return le projet mis à jour (HTTP 200), HTTP 400 si données invalides, HTTP 500 si erreur serveur
     */
    @PutMapping("/projets/{id}")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<?> updateProjet(
            @PathVariable Long id,
            @RequestBody UpdateProjetRequest req,
            Authentication auth) {
        try {
            return ResponseEntity.ok(projetService.updateProjet(id, req, auth));
        } catch (IllegalArgumentException e) {
            log.warn("updateProjet rejected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("updateProjet failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Supprime un projet et toutes ses données associées (tâches, membres).
     *
     * @param id   identifiant du projet à supprimer
     * @param auth contexte d'authentification pour le contrôle d'accès
     * @return HTTP 204 si supprimé avec succès
     */
    @DeleteMapping("/projets/{id}")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<Void> deleteProjet(
            @PathVariable Long id,
            Authentication auth) {
        projetService.deleteProjet(id, auth);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retourne la liste de tous les employés disponibles pour affectation à un projet.
     *
     * @param auth contexte d'authentification
     * @return liste des employés (HTTP 200)
     */
    @GetMapping("/employes")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<List<EmployeDTO>> getEmployes(Authentication auth) {
        return ResponseEntity.ok(projetService.getEmployes(auth));
    }

    /**
     * Ajoute des membres à un projet existant.
     *
     * @param id          identifiant du projet
     * @param employeeIds liste des identifiants d'employés à ajouter
     * @param auth        contexte d'authentification pour le contrôle d'accès
     * @return le projet mis à jour (HTTP 200), HTTP 409 si conflit, HTTP 500 si erreur serveur
     */
    @PostMapping("/projets/{id}/membres")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<?> addMembres(
            @PathVariable Long id,
            @RequestBody List<Long> employeeIds,
            Authentication auth) {
        try {
            UpdateProjetRequest req = new UpdateProjetRequest();
            req.setMembreIds(employeeIds);
            return ResponseEntity.ok(projetService.updateProjet(id, req, auth));
        } catch (IllegalArgumentException e) {
            log.warn("addMembres rejected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("addMembres failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Crée une nouvelle tâche dans un projet.
     *
     * @param req  données de création de la tâche (titre, projectId, priorité, dates...)
     * @param auth contexte d'authentification pour le contrôle d'accès
     * @return la tâche créée (HTTP 201)
     */
    @PostMapping("/taches")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<TacheDTO> createTache(
            @Valid @RequestBody CreateTacheRequest req,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projetService.createTache(req, auth));
    }

    /**
     * Met à jour une tâche existante.
     *
     * @param id   identifiant de la tâche à modifier
     * @param req  nouvelles données de la tâche
     * @param auth contexte d'authentification pour le contrôle d'accès
     * @return la tâche mise à jour (HTTP 200)
     */
    @PutMapping("/taches/{id}")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<TacheDTO> updateTache(
            @PathVariable Long id,
            @RequestBody CreateTacheRequest req,
            Authentication auth) {
        return ResponseEntity.ok(projetService.updateTache(id, req, auth));
    }

    /**
     * Assigne une tâche à un employé spécifique.
     *
     * @param id   identifiant de la tâche
     * @param body map JSON contenant la clé {@code employeeId}
     * @param auth contexte d'authentification pour le contrôle d'accès
     * @return la tâche mise à jour avec l'assignation (HTTP 200)
     */
    @PostMapping("/taches/{id}/assign")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<TacheDTO> assignTache(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body,
            Authentication auth) {
        return ResponseEntity.ok(projetService.assignTache(id, body.get("employeeId"), auth));
    }

    /**
     * Supprime une tâche.
     *
     * @param id   identifiant de la tâche à supprimer
     * @param auth contexte d'authentification pour le contrôle d'accès
     * @return HTTP 204 si supprimée avec succès
     */
    @DeleteMapping("/taches/{id}")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<Void> deleteTache(
            @PathVariable Long id,
            Authentication auth) {
        projetService.deleteTache(id, auth);
        return ResponseEntity.noContent().build();
    }
}