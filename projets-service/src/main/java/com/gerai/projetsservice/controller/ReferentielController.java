package com.gerai.projetsservice.controller;

import com.gerai.projetsservice.model.*;
import com.gerai.projetsservice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour la gestion du référentiel RH.
 * <p>
 * Expose les endpoints {@code /api/admin/referentiel} permettant le CRUD sur
 * les données de référence de la plateforme :
 * <ul>
 *   <li>Types de congé ({@code /types-conge}).</li>
 *   <li>Jours fériés ({@code /jours-feries}).</li>
 *   <li>Grille salariale ({@code /grille-salariale}).</li>
 *   <li>Compétences ({@code /competences}).</li>
 * </ul>
 * </p>
 * <p>
 * Les opérations de lecture (GET) sont ouvertes à tous les rôles authentifiés ;
 * les opérations de modification nécessitent le rôle RH, ADMIN ou ADMIN_RH.
 * </p>
 * <p>
 * {@code @RestController} : contrôleur REST retournant du JSON.
 * </p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/admin/referentiel")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class ReferentielController {

    private final TypeCongeRepository       typeCongeRepo;
    private final JourFerieRepository       jourFerieRepo;
    private final GrilleSalarialeRepository grilleRepo;
    private final CompetenceRepository      competenceRepo;

    /* ── Types de congé ──────────────────────────────────── */

    /**
     * Retourne tous les types de congé.
     *
     * @return liste des types de congé (HTTP 200)
     */
    @GetMapping("/types-conge")
    public ResponseEntity<List<TypeConge>> getTypesConge() {
        return ResponseEntity.ok(typeCongeRepo.findAll());
    }

    /**
     * Crée un nouveau type de congé (actif par défaut).
     *
     * @param body données du type de congé
     * @return le type de congé créé (HTTP 201)
     */
    @PostMapping("/types-conge")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<TypeConge> createTypeConge(@RequestBody TypeConge body) {
        body.setId(null);
        if (body.getActif() == null) body.setActif(true);
        return ResponseEntity.status(HttpStatus.CREATED).body(typeCongeRepo.save(body));
    }

    /**
     * Met à jour partiellement un type de congé (seuls les champs non nuls sont modifiés).
     *
     * @param id   identifiant du type de congé
     * @param body champs à modifier
     * @return le type de congé mis à jour (HTTP 200), HTTP 404 si introuvable
     */
    @PutMapping("/types-conge/{id}")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<TypeConge> updateTypeConge(@PathVariable Long id, @RequestBody TypeConge body) {
        return typeCongeRepo.findById(id).map(t -> {
            if (body.getCode()        != null) t.setCode(body.getCode());
            if (body.getLibelle()     != null) t.setLibelle(body.getLibelle());
            if (body.getDescription() != null) t.setDescription(body.getDescription());
            if (body.getNombreJours() != null) t.setNombreJours(body.getNombreJours());
            if (body.getPaye()        != null) t.setPaye(body.getPaye());
            if (body.getActif()       != null) t.setActif(body.getActif());
            if (body.getCouleur()     != null) t.setCouleur(body.getCouleur());
            if (body.getIcone()       != null) t.setIcone(body.getIcone());
            return ResponseEntity.ok(typeCongeRepo.save(t));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Supprime un type de congé.
     *
     * @param id identifiant du type de congé à supprimer
     * @return HTTP 204 si supprimé, HTTP 404 si introuvable
     */
    @DeleteMapping("/types-conge/{id}")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Void> deleteTypeConge(@PathVariable Long id) {
        if (!typeCongeRepo.existsById(id)) return ResponseEntity.notFound().build();
        typeCongeRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /* ── Jours fériés ────────────────────────────────────── */

    /**
     * Retourne tous les jours fériés.
     *
     * @return liste des jours fériés (HTTP 200)
     */
    @GetMapping("/jours-feries")
    public ResponseEntity<List<JourFerie>> getJoursFeries() {
        return ResponseEntity.ok(jourFerieRepo.findAll());
    }

    /**
     * Crée un nouveau jour férié.
     *
     * @param body données du jour férié (libellé, date, récurrence...)
     * @return le jour férié créé (HTTP 201)
     */
    @PostMapping("/jours-feries")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<JourFerie> createJourFerie(@RequestBody JourFerie body) {
        body.setId(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(jourFerieRepo.save(body));
    }

    /**
     * Met à jour partiellement un jour férié.
     *
     * @param id   identifiant du jour férié
     * @param body champs à modifier (libellé, date, récurrence, description)
     * @return le jour férié mis à jour (HTTP 200), HTTP 404 si introuvable
     */
    @PutMapping("/jours-feries/{id}")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<JourFerie> updateJourFerie(@PathVariable Long id, @RequestBody JourFerie body) {
        return jourFerieRepo.findById(id).map(j -> {
            if (body.getLibelle()     != null) j.setLibelle(body.getLibelle());
            if (body.getDate()        != null) j.setDate(body.getDate());
            if (body.getRecurrent()   != null) j.setRecurrent(body.getRecurrent());
            if (body.getDescription() != null) j.setDescription(body.getDescription());
            return ResponseEntity.ok(jourFerieRepo.save(j));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Supprime un jour férié.
     *
     * @param id identifiant du jour férié à supprimer
     * @return HTTP 204 si supprimé, HTTP 404 si introuvable
     */
    @DeleteMapping("/jours-feries/{id}")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Void> deleteJourFerie(@PathVariable Long id) {
        if (!jourFerieRepo.existsById(id)) return ResponseEntity.notFound().build();
        jourFerieRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /* ── Grille salariale ────────────────────────────────── */

    /**
     * Retourne toutes les entrées de la grille salariale.
     *
     * @return liste des entrées sérialisées en map (HTTP 200)
     */
    @GetMapping("/grille-salariale")
    public ResponseEntity<List<Map<String, Object>>> getGrille() {
        return ResponseEntity.ok(grilleRepo.findAll().stream().map(this::toGrilleMap).toList());
    }

    /**
     * Crée une nouvelle entrée dans la grille salariale.
     *
     * @param body map JSON avec {@code niveau}, {@code intitule}, {@code salaireMin},
     *             {@code salaireMax}, {@code salaireBase} et {@code avantages}
     * @return l'entrée créée (HTTP 201)
     */
    @PostMapping("/grille-salariale")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Map<String, Object>> createGrille(@RequestBody Map<String, Object> body) {
        GrilleSalariale g = new GrilleSalariale();
        if (body.containsKey("niveau"))      g.setNiveau(body.get("niveau").toString());
        if (body.containsKey("intitule"))     g.setIntitule(body.get("intitule").toString());
        if (body.containsKey("salaireMin"))   g.setSalaireMin(toDouble(body.get("salaireMin")));
        if (body.containsKey("salaireMax"))   g.setSalaireMax(toDouble(body.get("salaireMax")));
        if (body.containsKey("salaireBase"))  g.setSalaireBase(toDouble(body.get("salaireBase")));
        if (body.containsKey("avantages")) {
            Object av = body.get("avantages");
            if (av instanceof List<?> list) g.setAvantages(String.join(",", list.stream().map(Object::toString).toList()));
        }
        return ResponseEntity.status(201).body(toGrilleMap(grilleRepo.save(g)));
    }

    /**
     * Met à jour les données salariales d'une entrée de la grille.
     *
     * @param id   identifiant de l'entrée
     * @param body map JSON avec {@code niveau}, {@code intitule}, {@code salaireMin},
     *             {@code salaireMax}, {@code salaireBase} et {@code avantages}
     * @return l'entrée mise à jour (HTTP 200), HTTP 404 si introuvable
     */
    @PutMapping("/grille-salariale/{id}")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Map<String, Object>> updateGrille(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return grilleRepo.findById(id).map(g -> {
            if (body.containsKey("niveau"))      g.setNiveau(body.get("niveau").toString());
            if (body.containsKey("intitule"))     g.setIntitule(body.get("intitule").toString());
            if (body.containsKey("salaireMin"))   g.setSalaireMin(toDouble(body.get("salaireMin")));
            if (body.containsKey("salaireMax"))   g.setSalaireMax(toDouble(body.get("salaireMax")));
            if (body.containsKey("salaireBase"))  g.setSalaireBase(toDouble(body.get("salaireBase")));
            if (body.containsKey("avantages")) {
                Object av = body.get("avantages");
                if (av instanceof List<?> list) g.setAvantages(String.join(",", list.stream().map(Object::toString).toList()));
            }
            return ResponseEntity.ok(toGrilleMap(grilleRepo.save(g)));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Supprime une entrée de la grille salariale.
     *
     * @param id identifiant de l'entrée à supprimer
     * @return HTTP 204 si supprimée, HTTP 404 si introuvable
     */
    @DeleteMapping("/grille-salariale/{id}")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Void> deleteGrille(@PathVariable Long id) {
        if (!grilleRepo.existsById(id)) return ResponseEntity.notFound().build();
        grilleRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /* ── Compétences ─────────────────────────────────────── */

    /**
     * Retourne toutes les compétences du référentiel.
     *
     * @return liste des compétences (HTTP 200)
     */
    @GetMapping("/competences")
    public ResponseEntity<List<Competence>> getCompetences() {
        return ResponseEntity.ok(competenceRepo.findAll());
    }

    /**
     * Crée une nouvelle compétence (active par défaut).
     *
     * @param body données de la compétence (nom, catégorie, description...)
     * @return la compétence créée (HTTP 201)
     */
    @PostMapping("/competences")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Competence> createCompetence(@RequestBody Competence body) {
        body.setId(null);
        if (body.getActif() == null) body.setActif(true);
        return ResponseEntity.status(HttpStatus.CREATED).body(competenceRepo.save(body));
    }

    /**
     * Met à jour partiellement une compétence.
     *
     * @param id   identifiant de la compétence
     * @param body champs à modifier (nom, catégorie, description, actif)
     * @return la compétence mise à jour (HTTP 200), HTTP 404 si introuvable
     */
    @PutMapping("/competences/{id}")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Competence> updateCompetence(@PathVariable Long id, @RequestBody Competence body) {
        return competenceRepo.findById(id).map(c -> {
            if (body.getNom()         != null) c.setNom(body.getNom());
            if (body.getCategorie()   != null) c.setCategorie(body.getCategorie());
            if (body.getDescription() != null) c.setDescription(body.getDescription());
            if (body.getActif()       != null) c.setActif(body.getActif());
            return ResponseEntity.ok(competenceRepo.save(c));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Supprime une compétence du référentiel.
     *
     * @param id identifiant de la compétence à supprimer
     * @return HTTP 204 si supprimée, HTTP 404 si introuvable
     */
    @DeleteMapping("/competences/{id}")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Void> deleteCompetence(@PathVariable Long id) {
        if (!competenceRepo.existsById(id)) return ResponseEntity.notFound().build();
        competenceRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /* ── Helpers ─────────────────────────────────────────── */

    /**
     * Sérialise une entrée de grille salariale en map JSON.
     * <p>
     * Désérialise également la liste d'avantages stockée en CSV.
     * </p>
     *
     * @param g l'entrée de grille salariale à sérialiser
     * @return map avec les clés {@code id}, {@code salaireMin}, {@code salaireMax},
     *         {@code salaireBase} et {@code avantages}
     */
    private Map<String, Object> toGrilleMap(GrilleSalariale g) {
        List<String> avantages = (g.getAvantages() != null && !g.getAvantages().isBlank())
                ? Arrays.asList(g.getAvantages().split(","))
                : List.of();
        Map<String, Object> map = new HashMap<>();
        map.put("id",          g.getId());
        map.put("niveau",      g.getNiveau()    != null ? g.getNiveau()    : "");
        map.put("intitule",    g.getIntitule()  != null ? g.getIntitule()  : "");
        map.put("salaireMin",  g.getSalaireMin()  != null ? g.getSalaireMin()  : 0);
        map.put("salaireMax",  g.getSalaireMax()  != null ? g.getSalaireMax()  : 0);
        map.put("salaireBase", g.getSalaireBase() != null ? g.getSalaireBase() : 0);
        map.put("avantages",   avantages);
        return map;
    }

    /**
     * Convertit un objet JSON (Number ou String) en {@code Double}.
     *
     * @param val valeur à convertir
     * @return la valeur {@code Double} correspondante, ou {@code null} si nulle ou non convertible
     */
    private Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return null; }
    }
}
