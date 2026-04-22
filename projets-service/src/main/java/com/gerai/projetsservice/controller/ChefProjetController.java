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
 * ═══════════════════════════════════════════════════════════
 * ChefProjetController  —  /api/affectation
 *
 * Consommé par le ProjetService Angular de l'espace Chef :
 *   GET    /api/affectation/projets
 *   POST   /api/affectation/projets
 *   PUT    /api/affectation/projets/{id}
 *   DELETE /api/affectation/projets/{id}
 *   GET    /api/affectation/employes
 *   POST   /api/affectation/projets/{id}/membres
 *   POST   /api/affectation/taches
 *   PUT    /api/affectation/taches/{id}
 *   POST   /api/affectation/taches/{id}/assign
 * ═══════════════════════════════════════════════════════════
 */
@Slf4j
@RestController
@RequestMapping("/api/affectation")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
class ChefProjetController {

    private final ProjetService projetService;

    @GetMapping("/projets")
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<List<ProjetDTO>> getProjets(Authentication auth) {
        return ResponseEntity.ok(projetService.getProjetsChef(auth));
    }

    @PostMapping("/projets")
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<ProjetDTO> createProjet(
            @Valid @RequestBody CreateProjetRequest req,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projetService.createProjet(req, auth));
    }

    @PutMapping("/projets/{id}")
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<ProjetDTO> updateProjet(
            @PathVariable Long id,
            @RequestBody UpdateProjetRequest req,
            Authentication auth) {
        return ResponseEntity.ok(projetService.updateProjet(id, req, auth));
    }

    @DeleteMapping("/projets/{id}")
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<Void> deleteProjet(
            @PathVariable Long id,
            Authentication auth) {
        projetService.deleteProjet(id, auth);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/employes")
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<List<EmployeDTO>> getEmployes() {
        return ResponseEntity.ok(projetService.getEmployes());
    }

    @PostMapping("/projets/{id}/membres")
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<ProjetDTO> addMembres(
            @PathVariable Long id,
            @RequestBody List<Long> employeeIds,
            Authentication auth) {
        UpdateProjetRequest req = new UpdateProjetRequest();
        req.setMembreIds(employeeIds);
        return ResponseEntity.ok(projetService.updateProjet(id, req, auth));
    }

    @PostMapping("/taches")
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<TacheDTO> createTache(
            @Valid @RequestBody CreateTacheRequest req,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projetService.createTache(req, auth));
    }

    @PutMapping("/taches/{id}")
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<TacheDTO> updateTache(
            @PathVariable Long id,
            @RequestBody CreateTacheRequest req,
            Authentication auth) {
        return ResponseEntity.ok(projetService.updateTache(id, req, auth));
    }

    @PostMapping("/taches/{id}/assign")
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<TacheDTO> assignTache(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body,
            Authentication auth) {
        return ResponseEntity.ok(projetService.assignTache(id, body.get("employeeId"), auth));
    }
}