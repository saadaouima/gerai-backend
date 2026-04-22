package com.gerai.tachesservice.controller;

import com.gerai.tachesservice.dto.*;
import com.gerai.tachesservice.service.TacheService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints espace Employé :
 *   GET   /api/taches          → toutes les tâches de l'employé connecté
 *   GET   /api/taches/actives  → tâches non terminées
 *   PATCH /api/taches/{id}     → drag&drop Kanban (statut + progression)
 *   PUT   /api/taches/{id}/statut → mise à jour statut simple
 *
 * Correspond aux appels du TacheService Angular (ListeTachesComponent) :
 *   getTaches()            → GET  /api/taches
 *   getTachesActives()     → GET  /api/taches/actives
 *   updateTache(id, body)  → PATCH /api/taches/{id}
 *   updateStatut(id, s)    → PUT  /api/taches/{id}/statut
 */
@Slf4j
@RestController
@RequestMapping("/api/taches")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class TacheController {

    private final TacheService tacheService;

    /**
     * GET /api/taches
     * Angular : TacheService.getTaches()
     * Retourne toutes les tâches assignées à l'employé connecté.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN')")
    public ResponseEntity<List<TacheDTO>> getTaches(Authentication auth) {
        log.info("[Tache] GET /api/taches | user={}", auth.getName());
        return ResponseEntity.ok(tacheService.getTachesEmploye(auth));
    }

    /**
     * GET /api/taches/actives
     * Angular : TacheService.getTachesActives()
     * Tâches non terminées / non bloquées, triées par échéance.
     */
    @GetMapping("/actives")
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN')")
    public ResponseEntity<List<TacheDTO>> getTachesActives(Authentication auth) {
        log.info("[Tache] GET /api/taches/actives | user={}", auth.getName());
        return ResponseEntity.ok(tacheService.getTachesActives(auth));
    }

    /**
     * PATCH /api/taches/{id}
     * Angular : TacheService.updateTache(id, { statut, progression })
     * Déclenché par le drag&drop Kanban (ListeTachesComponent.onDrop()).
     *
     * Body : { "statut": "EN_COURS", "progression": 1 }
     *
     * Correspond au mock MSW :
     *   http.patch('/api/taches/:id', async ({ params, request }) => { ... }) **/

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN')")
    public ResponseEntity<TacheDTO> patchTache(
            @PathVariable Long id,
            @RequestBody StatutUpdateRequest request,
            Authentication auth) {
        log.info("[Tache] PATCH /api/taches/{} | statut={}", id, request.getStatut());
        return ResponseEntity.ok(tacheService.patchStatut(id, request, auth));
    }

    /**
     * PUT /api/taches/{id}/statut
     * Angular : TacheService.updateStatut(id, statut)
     *
     * Body : { "statut": "TERMINEE" }
     */
    @PutMapping("/{id}/statut")
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN')")
    public ResponseEntity<TacheDTO> updateStatut(
            @PathVariable Long id,
            @Valid @RequestBody StatutUpdateRequest request,
            Authentication auth) {
        log.info("[Tache] PUT /api/taches/{}/statut | statut={}", id, request.getStatut());
        return ResponseEntity.ok(tacheService.updateStatut(id, request, auth));
    }
}
