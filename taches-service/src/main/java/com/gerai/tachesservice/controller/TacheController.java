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
 * Contrôleur REST dédié à l'espace Employé pour la consultation et la mise à jour des tâches Kanban.
 * <p>
 * {@code @RestController} : combine {@code @Controller} et {@code @ResponseBody},
 * toutes les méthodes retournent directement du JSON sérialisé.
 * {@code @RequestMapping("/api/taches")} : préfixe commun à tous les endpoints de ce contrôleur.
 * {@code @RequiredArgsConstructor} : génère un constructeur injectant {@code TacheService} par Lombok.
 * {@code @CrossOrigin} : autorise les requêtes CORS depuis l'application Angular ({@code localhost:4200}).
 * <p>
 * Endpoints exposés :
 * <ul>
 *   <li>{@code GET /api/taches} → toutes les tâches assignées à l'employé connecté</li>
 *   <li>{@code GET /api/taches/actives} → tâches non terminées, triées par échéance</li>
 *   <li>{@code PATCH /api/taches/{id}} → déplacement Kanban (statut + progression)</li>
 *   <li>{@code PUT /api/taches/{id}/statut} → mise à jour simple du statut</li>
 * </ul>
 * <p>
 * Correspond aux appels Angular du composant {@code ListeTachesComponent} via {@code TacheService}.
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/taches")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class TacheController {

    /** Service métier de gestion des tâches, injecté par le constructeur Lombok. */
    private final TacheService tacheService;

    /**
     * Récupère toutes les tâches assignées à l'employé connecté.
     * <p>
     * Retourne une liste vide (et non une erreur) si aucune tâche n'est trouvée
     * ou si une exception survient, afin de ne pas bloquer l'affichage Angular.
     * <p>
     * Appelé par Angular : {@code TacheService.getTaches()}.
     *
     * @param auth le contexte d'authentification de l'employé connecté
     * @return {@code 200 OK} avec la liste des {@link TacheDTO} assignées à l'employé
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<TacheDTO>> getTaches(Authentication auth) {
        log.info("[Tache] GET /api/taches | user={}", auth.getName());
        try {
            return ResponseEntity.ok(tacheService.getTachesEmploye(auth));
        } catch (Exception e) {
            log.error("getTaches failed: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Récupère les tâches actives (non terminées, non bloquées) de l'employé connecté,
     * triées par date d'échéance croissante.
     * <p>
     * Utilisé par le widget de tableau de bord Angular pour afficher les tâches urgentes.
     * Retourne une liste vide en cas d'exception.
     * <p>
     * Appelé par Angular : {@code TacheService.getTachesActives()}.
     *
     * @param auth le contexte d'authentification de l'employé connecté
     * @return {@code 200 OK} avec la liste des {@link TacheDTO} actives, triées par échéance
     */
    @GetMapping("/actives")
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<TacheDTO>> getTachesActives(Authentication auth) {
        log.info("[Tache] GET /api/taches/actives | user={}", auth.getName());
        try {
            return ResponseEntity.ok(tacheService.getTachesActives(auth));
        } catch (Exception e) {
            log.error("getTachesActives failed: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Met à jour partiellement le statut et/ou la progression d'une tâche via le glisser-déposer Kanban.
     * <p>
     * Déclenché par {@code ListeTachesComponent.onDrop()} lors du déplacement d'une carte
     * entre les colonnes du tableau Kanban. Corps attendu :
     * {@code { "statut": "EN_COURS", "progression": 1 }}.
     * <p>
     * Un employé ne peut modifier que ses propres tâches (vérification effectuée dans le service).
     * Une notification est envoyée au chef si le statut change.
     * <p>
     * Appelé par Angular : {@code TacheService.updateTache(id, \{ statut, progression \})}.
     *
     * @param id      identifiant Oracle de la tâche à mettre à jour (TASKS.task_id)
     * @param request le DTO contenant le nouveau statut et la progression optionnelle
     * @param auth    le contexte d'authentification (vérifie que l'employé est bien l'assigné)
     * @return {@code 200 OK} avec le {@link TacheDTO} mis à jour
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<TacheDTO> patchTache(
            @PathVariable Long id,
            @RequestBody StatutUpdateRequest request,
            Authentication auth) {
        log.info("[Tache] PATCH /api/taches/{} | statut={}", id, request.getStatut());
        return ResponseEntity.ok(tacheService.patchStatut(id, request, auth));
    }

    /**
     * Met à jour le statut d'une tâche via un appel PUT explicite (alternative au PATCH Kanban).
     * <p>
     * Corps attendu : {@code { "statut": "TERMINEE" }}.
     * Délègue en interne à la même logique que {@link #patchTache}.
     * <p>
     * Appelé par Angular : {@code TacheService.updateStatut(id, statut)}.
     *
     * @param id      identifiant Oracle de la tâche (TASKS.task_id)
     * @param request le DTO contenant le nouveau statut (validé par {@code @Valid})
     * @param auth    le contexte d'authentification
     * @return {@code 200 OK} avec le {@link TacheDTO} mis à jour
     */
    @PutMapping("/{id}/statut")
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<TacheDTO> updateStatut(
            @PathVariable Long id,
            @Valid @RequestBody StatutUpdateRequest request,
            Authentication auth) {
        log.info("[Tache] PUT /api/taches/{}/statut | statut={}", id, request.getStatut());
        return ResponseEntity.ok(tacheService.updateStatut(id, request, auth));
    }
}
