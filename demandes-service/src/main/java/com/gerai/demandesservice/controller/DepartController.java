package com.gerai.demandesservice.controller;

import com.gerai.demandesservice.model.DepartEmploye;
import com.gerai.demandesservice.repository.DepartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Contrôleur REST gérant les départs d'employés (démission, licenciement, retraite,
 * fin de contrat CDD, mutation, décès) — préfixe {@code /api/departs}.
 * <p>
 * {@code @RestController} : toutes les méthodes retournent du JSON directement.
 * <p>
 * {@code @RequestMapping("/api/departs")} : préfixe commun à tous les endpoints.
 * <p>
 * {@code @CrossOrigin(origins = "*")} : autorise les requêtes cross-origin depuis toutes origines.
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/departs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DepartController {

    /** Repository JPA pour les départs employés (table DEPARTS_EMPLOYES). */
    private final DepartRepository departRepo;

    /**
     * Retourne la liste complète des départs enregistrés.
     *
     * @return liste de tous les départs avec statut HTTP 200
     */
    @GetMapping
    public ResponseEntity<List<DepartEmploye>> getAll() {
        return ResponseEntity.ok(departRepo.findAll());
    }

    /**
     * Retourne un départ par son identifiant.
     *
     * @param id identifiant technique du départ
     * @return le départ trouvé (200) ou 404 si inexistant
     */
    @GetMapping("/{id}")
    public ResponseEntity<DepartEmploye> getOne(@PathVariable Long id) {
        return departRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Enregistre un nouveau départ employé.
     * Le statut initial est {@code EN_COURS} si non précisé.
     *
     * @param body données du départ (employé, type, date, raison)
     * @return le départ créé avec statut HTTP 201 Created
     */
    @PostMapping
    public ResponseEntity<DepartEmploye> create(@RequestBody DepartEmploye body) {
        if (body.getStatut() == null) body.setStatut("EN_COURS");
        return ResponseEntity.status(HttpStatus.CREATED).body(departRepo.save(body));
    }

    /**
     * Met à jour les champs non nuls d'un départ existant (mise à jour partielle).
     * Seuls les champs présents dans le corps de la requête sont modifiés.
     *
     * @param id   identifiant du départ à modifier
     * @param body données partiellement mises à jour
     * @return le départ mis à jour (200) ou 404 si introuvable
     */
    @PutMapping("/{id}")
    public ResponseEntity<DepartEmploye> update(@PathVariable Long id, @RequestBody DepartEmploye body) {
        return departRepo.findById(id).map(d -> {
            if (body.getEmployeId()    != null) d.setEmployeId(body.getEmployeId());
            if (body.getEmployeNom()   != null) d.setEmployeNom(body.getEmployeNom());
            if (body.getEmployePoste() != null) d.setEmployePoste(body.getEmployePoste());
            if (body.getEmployePhoto() != null) d.setEmployePhoto(body.getEmployePhoto());
            if (body.getEmployeDept()  != null) d.setEmployeDept(body.getEmployeDept());
            if (body.getDateDepart()   != null) d.setDateDepart(body.getDateDepart());
            if (body.getTypeDepart()   != null) d.setTypeDepart(body.getTypeDepart());
            if (body.getRaison()       != null) d.setRaison(body.getRaison());
            if (body.getStatut()       != null) d.setStatut(body.getStatut());
            if (body.getNotes()        != null) d.setNotes(body.getNotes());
            return ResponseEntity.ok(departRepo.save(d));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Supprime un départ par son identifiant.
     *
     * @param id identifiant du départ à supprimer
     * @return 204 No Content si supprimé, 404 si introuvable
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!departRepo.existsById(id)) return ResponseEntity.notFound().build();
        departRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
