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
 * AdminProjetController  —  /api/admin
 *
 * Espace Admin RH : vision globale, évaluations, dashboard.
 *   GET    /api/admin/projets
 *   GET    /api/admin/projets/{id}/stats
 *   GET    /api/admin/evals
 *   POST   /api/admin/evals
 *   GET    /api/admin/dashboard
 * ═══════════════════════════════════════════════════════════
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
class AdminProjetController {

    private final ProjetService projetService;

    @GetMapping("/projets")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<List<ProjetDTO>> getAllProjets() {
        return ResponseEntity.ok(projetService.getAllProjets());
    }

    @GetMapping("/projets/{id}/stats")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ProjetDTO> getProjetStats(
            @PathVariable Long id,
            Authentication auth) {
        return ResponseEntity.ok(projetService.getProjetById(id, auth));
    }

    @GetMapping("/evals")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<List<PerformanceEvalDTO>> getEvals() {
        return ResponseEntity.ok(projetService.getEvals());
    }

    @PostMapping("/evals")
    @PreAuthorize("hasAnyRole('RH','ADMIN','CHEF')")
    public ResponseEntity<PerformanceEvalDTO> createEval(
            @RequestBody PerformanceEvalDTO req,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projetService.createEval(req, auth));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<DashboardDTO> getDashboard() {
        return ResponseEntity.ok(projetService.getDashboard());
    }
}
