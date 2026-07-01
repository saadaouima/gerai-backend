package com.gerai.projetsservice.controller;

import com.gerai.projetsservice.model.Job;
import com.gerai.projetsservice.repository.JobRepository;
import com.gerai.projetsservice.service.CvScoringService;
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
 * Contrôleur REST pour la gestion des offres d'emploi (admin/RH/chef).
 * <p>
 * Expose les endpoints {@code /api/admin/jobs} pour :
 * <ul>
 *   <li>La consultation, création et mise à jour des offres d'emploi.</li>
 *   <li>La suppression des offres (rôles RH/ADMIN uniquement).</li>
 *   <li>La génération automatique de descriptions de poste par IA Groq.</li>
 * </ul>
 * Les chefs de projet créent des offres avec le statut {@code EN_ATTENTE} en attente
 * de validation RH ; les RH/ADMIN les créent directement en statut {@code OUVERT}.
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/jobs")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class AdminJobController {

    private final JobRepository    repo;
    private final CvScoringService scoringService;

    /**
     * Retourne toutes les offres d'emploi (tous statuts).
     *
     * @return liste complète des offres (HTTP 200)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH','CHEF')")
    public ResponseEntity<List<Job>> getAll() {
        return ResponseEntity.ok(repo.findAll());
    }

    /**
     * Crée une nouvelle offre d'emploi.
     * <p>
     * Le statut initial est {@code EN_ATTENTE} si le créateur est un chef de projet,
     * {@code OUVERT} pour les RH et administrateurs.
     * </p>
     *
     * @param body données de l'offre
     * @param auth contexte d'authentification pour déterminer le rôle du créateur
     * @return l'offre créée (HTTP 201)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH','CHEF')")
    public ResponseEntity<Job> create(@RequestBody Job body, Authentication auth) {
        body.setId(null);
        boolean isChef = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_CHEF"));
        if (body.getStatut() == null) body.setStatut(isChef ? "EN_ATTENTE" : "OUVERT");
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(body));
    }

    /**
     * Met à jour partiellement une offre d'emploi (seuls les champs non nuls sont modifiés).
     *
     * @param id   identifiant de l'offre
     * @param body champs à mettre à jour
     * @return l'offre mise à jour (HTTP 200), HTTP 404 si introuvable
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH','CHEF')")
    public ResponseEntity<Job> update(@PathVariable Long id, @RequestBody Job body) {
        return repo.findById(id).map(j -> {
            if (body.getTitre()           != null) j.setTitre(body.getTitre());
            if (body.getStatut()          != null) j.setStatut(body.getStatut());
            if (body.getDatePublication() != null) j.setDatePublication(body.getDatePublication());
            if (body.getRole()            != null) j.setRole(body.getRole());
            if (body.getPostes()          != null) j.setPostes(body.getPostes());
            if (body.getDepartement()     != null) j.setDepartement(body.getDepartement());
            if (body.getTypeContrat()     != null) j.setTypeContrat(body.getTypeContrat());
            if (body.getLieu()            != null) j.setLieu(body.getLieu());
            if (body.getDescription()     != null) j.setDescription(body.getDescription());
            return ResponseEntity.ok(repo.save(j));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Supprime une offre d'emploi.
     *
     * @param id identifiant de l'offre à supprimer
     * @return HTTP 204 si supprimée, HTTP 404 si introuvable
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Génère par IA (Groq LLM) une description professionnelle de poste.
     *
     * @param body map JSON avec {@code titre}, {@code departement}, {@code role} et {@code typeContrat}
     * @return map {@code {description}} avec le texte généré (HTTP 200), HTTP 500 si l'IA échoue
     */
    @PostMapping("/ai-description")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<?> aiDescription(@RequestBody Map<String, String> body) {
        try {
            String description = scoringService.generateJobDescription(
                body.getOrDefault("titre",       "Poste"),
                body.getOrDefault("departement", ""),
                body.getOrDefault("role",        "JUNIOR"),
                body.getOrDefault("typeContrat", "CDI")
            );
            return ResponseEntity.ok(Map.of("description", description));
        } catch (Exception e) {
            log.error("[AI Job Desc] Failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
