package com.gerai.tachesservice.controller;

import com.gerai.tachesservice.dto.*;
import com.gerai.tachesservice.service.TacheService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints espace Chef :
 *   GET    /api/affectation/projets           → liste des projets du chef
 *   GET    /api/affectation/taches            → toutes les tâches du chef
 *   GET    /api/affectation/taches?projet=X   → tâches d'un projet
 *   POST   /api/affectation/taches            → créer une tâche
 *   PUT    /api/affectation/taches/{id}       → modifier une tâche
 *   DELETE /api/affectation/taches/{id}       → supprimer une tâche
 *
 * Correspond exactement aux appels du TacheService Angular (AffectationTachesComponent) :
 *   getProjets()         → GET /api/affectation/projets
 *   getTaches()          → GET /api/affectation/taches
 *   getTachesByProjet(n) → GET /api/affectation/taches?projet={nom}
 *   createTache(t)       → POST /api/affectation/taches
 *   updateTache(id, t)   → PUT /api/affectation/taches/{id}
 *   deleteTache(id)      → DELETE /api/affectation/taches/{id}
 */
@Slf4j
@RestController
@RequestMapping("/api/affectation")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class AffectationController {

    private final TacheService tacheService;

    /* ── Projets ──────────────────────────────────────── */

    /**
     * GET /api/affectation/projets
     * Angular : TacheService.getProjets()
     * Retourne les projets du chef connecté (ou tous les projets pour RH/Admin).
     */
    @GetMapping("/projets")
    @PreAuthorize("hasAnyRole('CHEF','RH','ADMIN')")
    public ResponseEntity<List<ProjetDTO>> getProjets(Authentication auth) {
        log.info("[Affectation] GET /projets | user={}", auth.getName());

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_RH")
                        || a.getAuthority().equals("ROLE_ADMIN"));

        List<ProjetDTO> projets = isAdmin
                ? tacheService.getTousProjets()
                : tacheService.getProjetsChef(auth);

        return ResponseEntity.ok(projets);
    }

    /* ── Tâches ───────────────────────────────────────── */

    /**
     * GET /api/affectation/taches
     * GET /api/affectation/taches?projet={nom}
     * Angular : TacheService.getTaches() et getTachesByProjet(projetNom)
     */
    @GetMapping("/taches")
    @PreAuthorize("hasAnyRole('CHEF','RH','ADMIN')")
    public ResponseEntity<List<TacheDTO>> getTaches(
            @RequestParam(required = false) String projet,
            Authentication auth) {
        log.info("[Affectation] GET /taches | projet='{}' | user={}",
                projet, auth.getName());
        return ResponseEntity.ok(tacheService.getTachesChef(projet, auth));
    }

    /**
     * POST /api/affectation/taches
     * Angular : TacheService.createTache(tache)
     *
     * Body attendu depuis AffectationTachesComponent.saveTask() :
     * { titre, priorite, assigneA, echeance, projet }
     */
    @PostMapping("/taches")
    @PreAuthorize("hasAnyRole('CHEF','RH','ADMIN')")
    public ResponseEntity<TacheDTO> createTache(
            @Valid @RequestBody TacheRequest request,
            Authentication auth) {
        log.info("[Affectation] POST /taches | titre='{}' | projet='{}'",
                request.getTitre(), request.getProjet());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tacheService.createTache(request, auth));
    }

    /**
     * PUT /api/affectation/taches/{id}
     * Angular : TacheService.updateTache(id, tache)
     */
    @PutMapping("/taches/{id}")
    @PreAuthorize("hasAnyRole('CHEF','RH','ADMIN')")
    public ResponseEntity<TacheDTO> updateTache(
            @PathVariable Long id,
            @Valid @RequestBody TacheRequest request,
            Authentication auth) {
        log.info("[Affectation] PUT /taches/{}", id);
        return ResponseEntity.ok(tacheService.updateTache(id, request, auth));
    }

    /**
     * DELETE /api/affectation/taches/{id}
     * Angular : TacheService.deleteTache(id)
     * Retourne 204 No Content — correspond au mock MSW.
     */
    @DeleteMapping("/taches/{id}")
    @PreAuthorize("hasAnyRole('CHEF','RH','ADMIN')")
    public ResponseEntity<Void> deleteTache(
            @PathVariable Long id,
            Authentication auth) {
        log.info("[Affectation] DELETE /taches/{}", id);
        tacheService.deleteTache(id, auth);
        return ResponseEntity.noContent().build();
    }
}