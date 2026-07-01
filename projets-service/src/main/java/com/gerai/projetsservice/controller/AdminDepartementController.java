package com.gerai.projetsservice.controller;

import com.gerai.projetsservice.model.Departement;
import com.gerai.projetsservice.repository.DepartementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Contrôleur REST pour la gestion du référentiel des départements.
 * <p>
 * Expose les endpoints {@code /api/admin/departements} accessibles aux rôles
 * RH, ADMIN et ADMIN_RH. Permet la création, la mise à jour et la suppression
 * des départements de l'organisation.
 * </p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/admin/departements")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class AdminDepartementController {

    /** Repository JPA pour la gestion des départements (table {@code ADMIN_DEPARTEMENTS}). */
    private final DepartementRepository repo;

    /**
     * Retourne la liste de tous les départements.
     *
     * @return liste complète des départements (HTTP 200)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<Departement>> getAll() {
        return ResponseEntity.ok(repo.findAll());
    }

    /**
     * Crée un nouveau département.
     *
     * @param body données du département (l'identifiant est ignoré)
     * @return le département créé (HTTP 201)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Departement> create(@RequestBody Departement body) {
        body.setId(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(body));
    }

    /**
     * Met à jour partiellement un département existant (seuls les champs non nuls sont modifiés).
     *
     * @param id   identifiant du département à mettre à jour
     * @param body champs à mettre à jour
     * @return le département mis à jour (HTTP 200), ou HTTP 404 si introuvable
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Departement> update(@PathVariable Long id, @RequestBody Departement body) {
        return repo.findById(id).map(d -> {
            if (body.getNom()           != null) d.setNom(body.getNom());
            if (body.getResponsable()   != null) d.setResponsable(body.getResponsable());
            if (body.getTelephone()     != null) d.setTelephone(body.getTelephone());
            if (body.getEmail()         != null) d.setEmail(body.getEmail());
            if (body.getCapacite()      != null) d.setCapacite(body.getCapacite());
            if (body.getAnneeCreation() != null) d.setAnneeCreation(body.getAnneeCreation());
            if (body.getTotalEmployes() != null) d.setTotalEmployes(body.getTotalEmployes());
            if (body.getDescription()   != null) d.setDescription(body.getDescription());
            return ResponseEntity.ok(repo.save(d));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Supprime un département.
     *
     * @param id identifiant du département à supprimer
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
