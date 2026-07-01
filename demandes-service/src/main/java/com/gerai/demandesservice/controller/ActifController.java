package com.gerai.demandesservice.controller;

import com.gerai.demandesservice.model.Actif;
import com.gerai.demandesservice.model.DemandeActif;
import com.gerai.demandesservice.repository.ActifRepository;
import com.gerai.demandesservice.repository.DemandeActifRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST gérant les actifs informatiques (matériel IT) et les demandes
 * de réparation ou de renouvellement d'actifs.
 * <p>
 * {@code @RestController} : combine {@code @Controller} et {@code @ResponseBody},
 * toutes les méthodes retournent directement un objet sérialisé en JSON.
 * <p>
 * {@code @RequestMapping("/api/actifs")} : préfixe commun à tous les endpoints de ce contrôleur.
 * <p>
 * {@code @RequiredArgsConstructor} : injection des dépendances via le constructeur généré par Lombok.
 * <p>
 * {@code @CrossOrigin(origins = "*")} : autorise les requêtes cross-origin depuis n'importe quelle origine
 * (frontend Angular en développement).
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/actifs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ActifController {

    /** Repository JPA pour les actifs IT (table ACTIFS). */
    private final ActifRepository        actifRepo;
    /** Repository JPA pour les demandes d'actifs (table DEMANDES_ACTIFS). */
    private final DemandeActifRepository demandeActifRepo;

    /**
     * Retourne la liste complète des actifs IT.
     *
     * @return liste de tous les actifs avec statut HTTP 200
     */
    @GetMapping
    public ResponseEntity<List<Actif>> getAll() {
        return ResponseEntity.ok(actifRepo.findAll());
    }

    /**
     * Retourne les demandes d'actifs, filtrées optionnellement par employé.
     *
     * @param employeId identifiant de l'employé (optionnel) ; si null, retourne toutes les demandes
     * @return liste des demandes d'actifs correspondantes avec statut HTTP 200
     */
    @GetMapping("/demandes")
    public ResponseEntity<List<DemandeActif>> getDemandes(
            @RequestParam(required = false) Long employeId) {
        List<DemandeActif> list = (employeId != null)
                ? demandeActifRepo.findByEmployeId(employeId)
                : demandeActifRepo.findAll();
        return ResponseEntity.ok(list);
    }

    /**
     * Crée un nouvel actif IT.
     * Si le statut n'est pas précisé, il est initialisé à {@code DISPONIBLE}.
     *
     * @param actif données de l'actif à créer
     * @return l'actif créé et persisté avec statut HTTP 200
     */
    @PostMapping
    public ResponseEntity<Actif> create(@RequestBody Actif actif) {
        if (actif.getStatut() == null) actif.setStatut("DISPONIBLE");
        return ResponseEntity.ok(actifRepo.save(actif));
    }

    /**
     * Retourne un actif par son identifiant.
     *
     * @param id identifiant technique de l'actif
     * @return l'actif trouvé (200) ou 404 si inexistant
     */
    @GetMapping("/{id}")
    public ResponseEntity<Actif> getById(@PathVariable Long id) {
        return actifRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Met à jour tous les champs d'un actif existant.
     *
     * @param id   identifiant de l'actif à modifier
     * @param body données mises à jour de l'actif
     * @return l'actif mis à jour (200) ou 404 si introuvable
     */
    @PutMapping("/{id}")
    public ResponseEntity<Actif> update(@PathVariable Long id, @RequestBody Actif body) {
        return actifRepo.findById(id).map(a -> {
            a.setNom(body.getNom());
            a.setCategorie(body.getCategorie());
            a.setType(body.getType());
            a.setStatut(body.getStatut());
            a.setMarque(body.getMarque());
            a.setModele(body.getModele());
            a.setNumeroSerie(body.getNumeroSerie());
            a.setDescription(body.getDescription());
            a.setValeur(body.getValeur());
            a.setDateAcquisition(body.getDateAcquisition());
            a.setDateAttribution(body.getDateAttribution());
            a.setDateExpiration(body.getDateExpiration());
            a.setEmployeId(body.getEmployeId());
            a.setEmployeNom(body.getEmployeNom());
            return ResponseEntity.ok(actifRepo.save(a));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Supprime un actif par son identifiant.
     *
     * @param id identifiant de l'actif à supprimer
     * @return 204 No Content si supprimé, 404 si introuvable
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!actifRepo.existsById(id)) return ResponseEntity.notFound().build();
        actifRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Crée une demande de réparation ou de renouvellement d'actif.
     * L'identifiant est réinitialisé pour garantir la création (pas de mise à jour).
     * Le statut initial est {@code EN_ATTENTE}.
     *
     * @param demande données de la demande d'actif
     * @return la demande créée avec statut HTTP 200
     */
    @PostMapping("/demandes")
    public ResponseEntity<DemandeActif> createDemande(@RequestBody DemandeActif demande) {
        demande.setId(null);
        if (demande.getStatut() == null) demande.setStatut("EN_ATTENTE");
        return ResponseEntity.ok(demandeActifRepo.save(demande));
    }

    /**
     * Met à jour le statut et/ou le commentaire administrateur d'une demande d'actif.
     * La date de traitement est automatiquement mise à l'heure courante lors du changement de statut.
     *
     * @param id   identifiant de la demande d'actif
     * @param body map contenant les champs à modifier ({@code statut} et/ou {@code commentaireAdmin})
     * @return la demande mise à jour (200) ou 404 si introuvable
     */
    @PutMapping("/demandes/{id}")
    public ResponseEntity<DemandeActif> updateDemande(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return demandeActifRepo.findById(id).map(d -> {
            if (body.containsKey("statut")) {
                d.setStatut(body.get("statut"));
                d.setDateTraitement(java.time.LocalDateTime.now());
            }
            if (body.containsKey("commentaireAdmin")) d.setCommentaireAdmin(body.get("commentaireAdmin"));
            return ResponseEntity.ok(demandeActifRepo.save(d));
        }).orElse(ResponseEntity.notFound().build());
    }
}
