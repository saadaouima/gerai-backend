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
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<List<ProjetDTO>> getProjets(Authentication auth) {
        try {
            return ResponseEntity.ok(projetService.getProjetsChef(auth));
        } catch (Exception e) {
            log.error("getProjets failed: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

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

    @DeleteMapping("/projets/{id}")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<Void> deleteProjet(
            @PathVariable Long id,
            Authentication auth) {
        projetService.deleteProjet(id, auth);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/employes")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<List<EmployeDTO>> getEmployes(Authentication auth) {
        return ResponseEntity.ok(projetService.getEmployes(auth));
    }

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

    @PostMapping("/taches")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<TacheDTO> createTache(
            @Valid @RequestBody CreateTacheRequest req,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projetService.createTache(req, auth));
    }

    @PutMapping("/taches/{id}")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<TacheDTO> updateTache(
            @PathVariable Long id,
            @RequestBody CreateTacheRequest req,
            Authentication auth) {
        return ResponseEntity.ok(projetService.updateTache(id, req, auth));
    }

    @PostMapping("/taches/{id}/assign")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<TacheDTO> assignTache(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body,
            Authentication auth) {
        return ResponseEntity.ok(projetService.assignTache(id, body.get("employeeId"), auth));
    }

    @DeleteMapping("/taches/{id}")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<Void> deleteTache(
            @PathVariable Long id,
            Authentication auth) {
        projetService.deleteTache(id, auth);
        return ResponseEntity.noContent().build();
    }
}