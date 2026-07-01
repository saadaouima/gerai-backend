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
 * Contrôleur REST dédié à l'espace Chef de projet pour la gestion des tâches et projets.
 * <p>
 * {@code @RestController} : combine {@code @Controller} et {@code @ResponseBody},
 * toutes les méthodes retournent directement du JSON sérialisé.
 * {@code @RequestMapping("/api/affectation")} : préfixe commun à tous les endpoints de ce contrôleur.
 * {@code @RequiredArgsConstructor} : génère un constructeur injectant {@code TacheService} par Lombok.
 * {@code @CrossOrigin} : autorise les requêtes CORS depuis l'application Angular ({@code localhost:4200}).
 * <p>
 * Endpoints exposés :
 * <ul>
 *   <li>{@code GET /api/affectation/projets} → liste des projets du chef connecté</li>
 *   <li>{@code GET /api/affectation/taches} → toutes les tâches du chef</li>
 *   <li>{@code GET /api/affectation/taches?projet=X} → tâches filtrées par projet</li>
 *   <li>{@code POST /api/affectation/taches} → créer une tâche</li>
 *   <li>{@code PUT /api/affectation/taches/{id}} → modifier une tâche existante</li>
 *   <li>{@code DELETE /api/affectation/taches/{id}} → supprimer une tâche</li>
 * </ul>
 * <p>
 * Correspond aux appels Angular du composant {@code AffectationTachesComponent} via {@code TacheService}.
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/affectation")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class AffectationController {

    /** Service métier de gestion des tâches, injecté par le constructeur Lombok. */
    private final TacheService tacheService;

    /* ── Projets ──────────────────────────────────────── */

    /**
     * Récupère la liste des projets accessibles à l'utilisateur connecté.
     * <p>
     * Si l'utilisateur possède le rôle RH ou ADMIN, tous les projets de la plateforme
     * sont retournés. Pour un Chef, seuls les projets dont il est responsable sont retournés.
     * <p>
     * Appelé par Angular : {@code TacheService.getProjets()}.
     *
     * @param auth le contexte d'authentification Spring Security de l'utilisateur connecté
     * @return {@code 200 OK} avec la liste des {@link ProjetDTO} accessibles
     */
    @GetMapping("/projets")
    @PreAuthorize("hasAnyRole('CHEF','RH','ADMIN','ADMIN_RH')")
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
     * Récupère les tâches du chef connecté, avec filtre optionnel par nom de projet.
     * <p>
     * Sans paramètre {@code projet}, retourne toutes les tâches de tous les projets du chef.
     * Avec le paramètre {@code projet}, filtre les tâches appartenant au projet nommé.
     * <p>
     * Appelé par Angular : {@code TacheService.getTaches()} et {@code getTachesByProjet(projetNom)}.
     *
     * @param projet nom du projet servant de filtre (paramètre optionnel)
     * @param auth   le contexte d'authentification de l'utilisateur connecté
     * @return {@code 200 OK} avec la liste des {@link TacheDTO} correspondantes
     */
    @GetMapping("/taches")
    @PreAuthorize("hasAnyRole('CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<TacheDTO>> getTaches(
            @RequestParam(required = false) String projet,
            Authentication auth) {
        log.info("[Affectation] GET /taches | projet='{}' | user={}",
                projet, auth.getName());
        return ResponseEntity.ok(tacheService.getTachesChef(projet, auth));
    }

    /**
     * Crée une nouvelle tâche et l'assigne à un employé dans un projet.
     * <p>
     * Corps attendu depuis {@code AffectationTachesComponent.saveTask()} :
     * {@code { titre, priorite, assigneA, echeance, projet }}.
     * Une notification Kafka est envoyée à l'employé assigné après la création.
     * <p>
     * Appelé par Angular : {@code TacheService.createTache(tache)}.
     *
     * @param request le DTO de création de tâche validé par {@code @Valid}
     * @param auth    le contexte d'authentification (identifie le créateur de la tâche)
     * @return {@code 201 Created} avec le {@link TacheDTO} de la tâche créée
     */
    @PostMapping("/taches")
    @PreAuthorize("hasAnyRole('CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<TacheDTO> createTache(
            @Valid @RequestBody TacheRequest request,
            Authentication auth) {
        log.info("[Affectation] POST /taches | titre='{}' | projet='{}'",
                request.getTitre(), request.getProjet());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tacheService.createTache(request, auth));
    }

    /**
     * Met à jour une tâche existante (titre, priorité, assignation, échéance, etc.).
     * <p>
     * Si l'assigné change, une notification de réassignation est envoyée via Kafka.
     * <p>
     * Appelé par Angular : {@code TacheService.updateTache(id, tache)}.
     *
     * @param id      identifiant Oracle de la tâche à modifier (TASKS.task_id)
     * @param request le DTO contenant les nouvelles valeurs (champs null ignorés)
     * @param auth    le contexte d'authentification de l'utilisateur
     * @return {@code 200 OK} avec le {@link TacheDTO} mis à jour
     */
    @PutMapping("/taches/{id}")
    @PreAuthorize("hasAnyRole('CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<TacheDTO> updateTache(
            @PathVariable Long id,
            @Valid @RequestBody TacheRequest request,
            Authentication auth) {
        log.info("[Affectation] PUT /taches/{}", id);
        return ResponseEntity.ok(tacheService.updateTache(id, request, auth));
    }

    /**
     * Supprime définitivement une tâche.
     * <p>
     * Seul le Chef créateur du projet ou un Admin/RH peut supprimer une tâche.
     * Retourne {@code 204 No Content} en cas de succès.
     * <p>
     * Appelé par Angular : {@code TacheService.deleteTache(id)}.
     *
     * @param id   identifiant Oracle de la tâche à supprimer (TASKS.task_id)
     * @param auth le contexte d'authentification (vérifié pour les droits de suppression)
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/taches/{id}")
    @PreAuthorize("hasAnyRole('CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Void> deleteTache(
            @PathVariable Long id,
            Authentication auth) {
        log.info("[Affectation] DELETE /taches/{}", id);
        tacheService.deleteTache(id, auth);
        return ResponseEntity.noContent().build();
    }
}