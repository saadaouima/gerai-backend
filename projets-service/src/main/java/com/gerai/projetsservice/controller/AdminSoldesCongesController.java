package com.gerai.projetsservice.controller;

import com.gerai.projetsservice.model.SoldeCongeAdmin;
import com.gerai.projetsservice.repository.SoldeCongeAdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Contrôleur REST pour la gestion des soldes de congés administratifs.
 * <p>
 * Expose les endpoints {@code /api/admin/soldes-conges} permettant aux rôles
 * RH/ADMIN/ADMIN_RH de consulter, créer, mettre à jour et supprimer les
 * enregistrements de soldes de congés des employés (table {@code SOLDES_CONGES_ADMIN}).
 * </p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/admin/soldes-conges")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class AdminSoldesCongesController {

    private final SoldeCongeAdminRepository repo;

    /**
     * Retourne tous les soldes de congés enregistrés.
     *
     * @return liste complète des soldes (HTTP 200)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<SoldeCongeAdmin>> getAll() {
        return ResponseEntity.ok(repo.findAll());
    }

    /**
     * Crée un nouvel enregistrement de solde de congés.
     *
     * @param body données du solde (l'identifiant est ignoré)
     * @return le solde créé (HTTP 201)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<SoldeCongeAdmin> create(@RequestBody SoldeCongeAdmin body) {
        body.setId(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(body));
    }

    /**
     * Met à jour partiellement un solde de congés (seuls les champs non nuls sont modifiés).
     *
     * @param id   identifiant du solde
     * @param body nouveaux valeurs des champs de solde
     * @return le solde mis à jour (HTTP 200), HTTP 404 si introuvable
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<SoldeCongeAdmin> update(@PathVariable Long id, @RequestBody SoldeCongeAdmin body) {
        return repo.findById(id).map(s -> {
            if (body.getSoldePrecedent()  != null) s.setSoldePrecedent(body.getSoldePrecedent());
            if (body.getSoldeTotal()      != null) s.setSoldeTotal(body.getSoldeTotal());
            if (body.getReportSolde()     != null) s.setReportSolde(body.getReportSolde());
            if (body.getSoldeActuel()     != null) s.setSoldeActuel(body.getSoldeActuel());
            if (body.getCongesUtilises()  != null) s.setCongesUtilises(body.getCongesUtilises());
            if (body.getCongesAcceptes()  != null) s.setCongesAcceptes(body.getCongesAcceptes());
            if (body.getCongesRejetes()   != null) s.setCongesRejetes(body.getCongesRejetes());
            if (body.getCongesExpires()   != null) s.setCongesExpires(body.getCongesExpires());
            return ResponseEntity.ok(repo.save(s));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Supprime un enregistrement de solde de congés.
     *
     * @param id identifiant du solde à supprimer
     * @return HTTP 204 si supprimé, HTTP 404 si introuvable
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
