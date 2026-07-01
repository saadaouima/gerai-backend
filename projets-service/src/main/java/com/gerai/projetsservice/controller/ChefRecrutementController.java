package com.gerai.projetsservice.controller;

import com.gerai.projetsservice.model.DemandeRecrutement;
import com.gerai.projetsservice.repository.DemandeRecrutementRepository;
import com.gerai.projetsservice.repository.JobRepository;
import com.gerai.projetsservice.service.CvScoringService;
import com.gerai.projetsservice.service.RecruitmentNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Contrôleur REST pour la gestion des demandes de recrutement par le chef de projet.
 * <p>
 * Expose les endpoints {@code /api/chef/recrutement} permettant :
 * <ul>
 *   <li>La consultation des demandes de recrutement (par chef ou toutes).</li>
 *   <li>La création d'une nouvelle demande (statut initial {@code EN_ATTENTE}).</li>
 *   <li>La suppression d'une demande en attente.</li>
 *   <li>Le rejet d'une demande par la RH (avec motif optionnel).</li>
 *   <li>La conversion d'une demande acceptée en offre d'emploi avec description IA.</li>
 * </ul>
 * </p>
 * <p>
 * {@code @RestController} : contrôleur REST retournant du JSON.<br>
 * {@code @Slf4j} : journalisation SLF4J via Lombok.
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/chef/recrutement")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class ChefRecrutementController {

    private final DemandeRecrutementRepository   repo;
    private final JobRepository                  jobRepo;
    private final CvScoringService               scoringService;
    private final RecruitmentNotificationService recruitmentNotif;

    /**
     * Retourne les demandes de recrutement, filtrées par chef si {@code chefId} est fourni.
     *
     * @param chefId identifiant Keycloak du chef de projet (optionnel)
     * @return liste des demandes correspondantes (HTTP 200)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<List<DemandeRecrutement>> getDemandes(
            @RequestParam(required = false) String chefId) {
        if (chefId != null && !chefId.isBlank()) {
            return ResponseEntity.ok(repo.findByChefId(chefId));
        }
        return ResponseEntity.ok(repo.findAll());
    }

    /**
     * Crée une nouvelle demande de recrutement en statut {@code EN_ATTENTE}.
     *
     * @param body données de la demande (titre du poste, département, justification...)
     * @return la demande créée avec la date du jour (HTTP 201)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<DemandeRecrutement> create(@RequestBody DemandeRecrutement body) {
        body.setId(null);
        body.setStatut("EN_ATTENTE");
        body.setDateDemande(LocalDate.now());
        DemandeRecrutement saved = repo.save(body);
        recruitmentNotif.notifierNouvelleDemandeRecrutement(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Supprime une demande de recrutement uniquement si elle est en statut {@code EN_ATTENTE}.
     *
     * @param id identifiant de la demande à supprimer
     * @return HTTP 204 si supprimée, HTTP 404 si introuvable, HTTP 409 si statut non supprimable
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        DemandeRecrutement d = repo.findById(id).orElseThrow();
        if (!"EN_ATTENTE".equals(d.getStatut())) return ResponseEntity.<Void>status(HttpStatus.CONFLICT).build();
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Rejette une demande de recrutement en attente.
     *
     * @param id   identifiant de la demande
     * @param body map JSON optionnelle avec {@code motif} et {@code traiteePar}
     * @return la demande mise à jour en statut {@code REJETEE} (HTTP 200),
     *         HTTP 404 si introuvable, HTTP 409 si statut non modifiable
     */
    @PutMapping("/{id}/rejeter")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<DemandeRecrutement> rejeter(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        Optional<DemandeRecrutement> opt = repo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        DemandeRecrutement d = opt.get();
        if (!"EN_ATTENTE".equals(d.getStatut()))
            return ResponseEntity.<DemandeRecrutement>status(HttpStatus.CONFLICT).build();
        d.setStatut("REJETEE");
        d.setDateTraitement(LocalDate.now());
        if (body != null) {
            if (body.containsKey("motif"))       d.setCommentaireRh(body.get("motif"));
            if (body.containsKey("traiteePar"))  d.setTraiteePar(body.get("traiteePar"));
        }
        return ResponseEntity.ok(repo.save(d));
    }

    /**
     * Convertit une demande de recrutement acceptée en offre d'emploi publiée.
     * <p>
     * La description de l'offre est générée automatiquement par l'IA Groq (llama-3.3-70b-versatile).
     * En cas d'échec de l'IA, la justification de la demande est utilisée comme description de repli.
     * </p>
     *
     * @param id   identifiant de la demande à convertir
     * @param body map JSON optionnelle avec {@code traiteePar}
     * @return la demande mise à jour en statut {@code CONVERTIE} (HTTP 200), HTTP 404 si introuvable
     */
    @PostMapping("/{id}/convertir")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<DemandeRecrutement> convertir(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        return repo.findById(id).map(d -> {
            d.setStatut("CONVERTIE");
            d.setDateTraitement(LocalDate.now());
            if (body != null && body.containsKey("traiteePar"))
                d.setTraiteePar(body.get("traiteePar"));
            repo.save(d);

            // Auto-generate job description via Groq; fall back to justification if AI unavailable
            String description;
            try {
                description = scoringService.generateJobDescription(
                    d.getTitrePoste(),
                    d.getDepartement(),
                    d.getRole()        != null ? d.getRole()        : "JUNIOR",
                    d.getTypeContrat() != null ? d.getTypeContrat() : "CDI"
                );
                log.info("[Recrutement] AI description generated for '{}'", d.getTitrePoste());
            } catch (Exception e) {
                log.warn("[Recrutement] AI description failed, using justification: {}", e.getMessage());
                description = d.getJustification();
            }

            com.gerai.projetsservice.model.Job job = com.gerai.projetsservice.model.Job.builder()
                    .titre(d.getTitrePoste())
                    .statut("OUVERT")
                    .role(d.getRole())
                    .postes(d.getNombrePostes())
                    .departement(d.getDepartement())
                    .typeContrat(d.getTypeContrat())
                    .datePublication(LocalDate.now().toString())
                    .description(description)
                    .build();
            jobRepo.save(job);
            return ResponseEntity.ok(d);
        }).orElse(ResponseEntity.notFound().build());
    }
}
