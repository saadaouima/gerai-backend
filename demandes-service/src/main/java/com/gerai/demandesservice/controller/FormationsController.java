package com.gerai.demandesservice.controller;

import com.gerai.demandesservice.model.TrainingRequest;
import com.gerai.demandesservice.repository.EmployeeRepository;
import com.gerai.demandesservice.repository.TrainingRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Contrôleur REST gérant le cycle de vie complet des demandes de formation professionnelle —
 * préfixe {@code /api/formations}.
 * <p>
 * {@code @RestController} : toutes les méthodes retournent du JSON directement.
 * <p>
 * Workflow de formation : EN_ATTENTE → APPROUVE_CHEF → APPROUVE_RH → PLANIFIEE → EN_COURS → COMPLETEE.
 * Le chef approuve ou rejette en premier (étape 1), puis le RH confirme (étape 2),
 * puis le RH planifie, démarre et clôture la formation.
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/formations")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class FormationsController {

    /** Repository JPA pour les demandes de formation (table TRAINING_REQUESTS). */
    private final TrainingRequestRepository trainingRepo;
    /** Repository en lecture seule sur EMPLOYEES — résolution du nom et de l'identifiant. */
    private final EmployeeRepository        employeeRepo;

    /**
     * Retourne la liste des demandes de formation selon les filtres fournis :
     * <ul>
     *   <li>{@code GET /api/formations} → toutes les formations (admin/RH)</li>
     *   <li>{@code GET /api/formations?chefId=X} → formations de l'équipe du chef (résolution JWT)</li>
     *   <li>{@code GET /api/formations?employeId=X} → formations d'un employé spécifique</li>
     * </ul>
     *
     * @param chefId    identifiant Oracle du chef (optionnel — résolu depuis le JWT si fourni)
     * @param employeId identifiant Oracle de l'employé (optionnel)
     * @param auth      contexte d'authentification de l'utilisateur connecté
     * @return liste des formations correspondant aux critères, avec statut HTTP 200
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CHEF','EMPLOYE','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<List<Map<String, Object>>> getFormations(
            @RequestParam(required = false) Long chefId,
            @RequestParam(required = false) Long employeId,
            Authentication auth) {

        List<TrainingRequest> requests;
        if (chefId != null) {
            // Always resolve from JWT — the Angular-passed chefId may be 0 if the
            // employe-service didn't find the user (no email fallback there).
            Long resolved = resolveEmployeeId(auth);
            Long effectiveChefId = (resolved != null && resolved > 0) ? resolved : chefId;
            log.info("[Formations] chefId param={} resolved={} effective={}", chefId, resolved, effectiveChefId);
            requests = trainingRepo.findByManager(effectiveChefId);
        } else if (employeId != null) {
            requests = trainingRepo.findByEmployeeIdOrderByCreatedAtDesc(employeId);
        } else {
            requests = trainingRepo.findAll();
        }

        return ResponseEntity.ok(requests.stream().map(this::toDto).toList());
    }

    /**
     * Crée une nouvelle demande de formation soumise par un employé.
     * Accepte un corps JSON libre avec les champs : titre, organisme, objectifs,
     * lieu, mode, dureeJours, coutEstime, dateDebutSouhaitee, employeId.
     *
     * @param body map JSON contenant les données de la formation à créer
     * @return le DTO de la formation créée avec statut HTTP 201
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Map<String, Object>> creerFormation(
            @RequestBody Map<String, Object> body) {

        TrainingRequest tr = new TrainingRequest();

        Object empId = body.get("employeId");
        if (empId != null) tr.setEmployeeId(Long.parseLong(empId.toString()));

        tr.setTrainingTitle(str(body, "titre"));
        tr.setProvider(str(body, "organisme"));
        tr.setReason(str(body, "objectifs"));
        tr.setLieu(str(body, "lieu"));
        tr.setModeFormation(str(body, "mode"));

        Object duree = body.get("dureeJours");
        if (duree != null) tr.setDurationDays(Integer.parseInt(duree.toString()));

        Object cout = body.get("coutEstime");
        if (cout != null && !cout.toString().isBlank()) {
            tr.setEstimatedCost(new BigDecimal(cout.toString()));
        }

        String dateDebut = str(body, "dateDebutSouhaitee");
        if (dateDebut != null && !dateDebut.isBlank()) {
            tr.setPlannedDate(LocalDate.parse(dateDebut));
        }

        tr.setStatus("EN_ATTENTE");
        tr = trainingRepo.save(tr);
        log.info("[Formations] Créée | id={} | emp={}", tr.getRequestId(), tr.getEmployeeId());
        return ResponseEntity.status(201).body(toDto(tr));
    }

    /**
     * Enregistre la décision du chef sur une demande de formation (étape 1 du workflow).
     * <p>
     * La valeur du champ {@code decision} dans le corps doit être {@code APPROUVE_CHEF}
     * (pour approuver) ou {@code REJETE_CHEF} (pour rejeter).
     *
     * @param id   identifiant de la demande de formation
     * @param body map contenant le champ {@code decision}
     * @return la formation mise à jour (200) ou 404 si introuvable
     */
    @PutMapping("/{id}/chef-decision")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<Map<String, Object>> chefDecision(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        return trainingRepo.findById(id).map(tr -> {
            String decision = body.getOrDefault("decision", "");
            tr.setStatus(switch (decision) {
                case "APPROUVE_CHEF" -> "APPROUVE_CHEF";
                case "REJETE_CHEF"   -> "REFUSE";
                default              -> tr.getStatus();
            });
            return ResponseEntity.ok(toDto(trainingRepo.save(tr)));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Enregistre la décision du RH sur une demande de formation préalablement approuvée par le chef (étape 2).
     * Enregistre également le nom du gestionnaire RH et la date de traitement.
     *
     * @param id   identifiant de la demande de formation
     * @param body map contenant {@code decision} ({@code APPROUVE_RH} ou {@code REJETE_RH})
     *             et optionnellement {@code traiteeParRh} (nom du gestionnaire)
     * @return la formation mise à jour (200) ou 404 si introuvable
     */
    @PutMapping("/{id}/rh-decision")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Map<String, Object>> rhDecision(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        return trainingRepo.findById(id).map(tr -> {
            String decision = body.getOrDefault("decision", "");
            tr.setStatus(switch (decision) {
                case "APPROUVE_RH" -> "APPROUVE_RH";
                case "REJETE_RH"   -> "REFUSE";
                default            -> tr.getStatus();
            });
            tr.setApprovedByRhName(body.getOrDefault("traiteeParRh", null));
            tr.setApprovedAtRh(LocalDateTime.now());
            return ResponseEntity.ok(toDto(trainingRepo.save(tr)));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Planifie une formation approuvée : passe le statut à {@code PLANIFIEE}
     * et enregistre la date réelle de début.
     *
     * @param id   identifiant de la formation
     * @param body map contenant optionnellement {@code dateDebutReelle} (format {@code yyyy-MM-dd})
     * @return la formation planifiée (200) ou 404 si introuvable
     */
    @PutMapping("/{id}/planifier")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Map<String, Object>> planifier(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        return trainingRepo.findById(id).map(tr -> {
            tr.setStatus("PLANIFIEE");
            String dateDebut = body.get("dateDebutReelle");
            if (dateDebut != null) tr.setPlannedDate(LocalDate.parse(dateDebut));
            return ResponseEntity.ok(toDto(trainingRepo.save(tr)));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Démarre une formation planifiée : passe le statut à {@code EN_COURS}.
     *
     * @param id identifiant de la formation à démarrer
     * @return la formation en cours (200) ou 404 si introuvable
     */
    @PutMapping("/{id}/demarrer")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Map<String, Object>> demarrer(@PathVariable Long id) {
        return trainingRepo.findById(id).map(tr -> {
            tr.setStatus("EN_COURS");
            return ResponseEntity.ok(toDto(trainingRepo.save(tr)));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Marque une formation comme terminée : passe le statut à {@code COMPLETEE}.
     *
     * @param id   identifiant de la formation
     * @param body corps optionnel (non utilisé, prévu pour extension future)
     * @return la formation complétée (200) ou 404 si introuvable
     */
    @PutMapping("/{id}/completer")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Map<String, Object>> completer(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {

        return trainingRepo.findById(id).map(tr -> {
            tr.setStatus("COMPLETEE");
            return ResponseEntity.ok(toDto(trainingRepo.save(tr)));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Annule une formation : passe le statut à {@code ANNULE}.
     *
     * @param id identifiant de la formation à annuler
     * @return la formation annulée (200) ou 404 si introuvable
     */
    @PutMapping("/{id}/annuler")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Map<String, Object>> annuler(@PathVariable Long id) {
        return trainingRepo.findById(id).map(tr -> {
            tr.setStatus("ANNULE");
            return ResponseEntity.ok(toDto(trainingRepo.save(tr)));
        }).orElse(ResponseEntity.notFound().build());
    }

    /* ══════════════════════════════════════════════════
       HELPERS
       ══════════════════════════════════════════════════ */

    /**
     * Resolves the caller's EMPLOYEE_ID from their JWT.
     * Mirrors JwtHelper in projets-service: tries sub first, then email.
     */
    private Long resolveEmployeeId(Authentication auth) {
        if (!(auth.getPrincipal() instanceof Jwt jwt)) return null;
        try {
            String sub = jwt.getSubject();
            if (sub != null) {
                Long id = employeeRepo.findEmployeeIdByKeycloakSub(sub);
                if (id != null) return id;
            }
            String email = jwt.getClaimAsString("email");
            if (email != null) {
                Long id = employeeRepo.findEmployeeIdByEmail(email);
                if (id != null) return id;
            }
        } catch (Exception e) {
            log.warn("[Formations] JWT employee resolution failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Convertit une entité {@link TrainingRequest} en DTO Map compatible avec le frontend Angular.
     * Résout le nom complet de l'employé via la base de données.
     *
     * @param tr l'entité demande de formation à convertir
     * @return map de données prête à sérialiser en JSON
     */
    private Map<String, Object> toDto(TrainingRequest tr) {
        String nom = "", prenom = "";
        try {
            String full = employeeRepo.findFullNameByEmployeeId(tr.getEmployeeId());
            if (full != null) {
                int sp = full.indexOf(' ');
                if (sp > 0) { prenom = full.substring(0, sp); nom = full.substring(sp + 1); }
                else { nom = full; }
            }
        } catch (Exception e) {
            log.warn("Name lookup failed for employee {}", tr.getEmployeeId());
        }

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id",                 tr.getRequestId());
        dto.put("employeId",          tr.getEmployeeId());
        dto.put("employeNom",         nom);
        dto.put("employePrenom",      prenom);
        dto.put("departement",        "");
        dto.put("titre",              tr.getTrainingTitle() != null ? tr.getTrainingTitle() : "");
        dto.put("organisme",          tr.getProvider()      != null ? tr.getProvider()      : "");
        dto.put("type",               "FORMATION");
        dto.put("mode",               tr.getModeFormation() != null ? tr.getModeFormation() : "PRESENTIEL");
        dto.put("dureeJours",         tr.getDurationDays()  != null ? tr.getDurationDays()  : 0);
        dto.put("coutEstime",         tr.getEstimatedCost());
        dto.put("dateDebutSouhaitee", tr.getPlannedDate()   != null ? tr.getPlannedDate().toString() : null);
        dto.put("objectifs",          tr.getReason()        != null ? tr.getReason()        : "");
        dto.put("statut",             mapStatut(tr.getStatus()));
        dto.put("dateDemande",        tr.getCreatedAt()     != null
                                          ? tr.getCreatedAt().toLocalDate().toString()
                                          : LocalDate.now().toString());
        dto.put("commentaireChef",    null);
        dto.put("traiteeParRh",       tr.getApprovedByRhName());
        dto.put("dateTraitementRh",   tr.getApprovedAtRh() != null ? tr.getApprovedAtRh().toString() : null);
        return dto;
    }

    /**
     * Extrait la valeur d'une clé d'une map en tant que {@link String}.
     *
     * @param body map JSON du corps de la requête
     * @param key  clé à extraire
     * @return la valeur sous forme de chaîne, ou {@code null} si la clé est absente
     */
    private String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v != null ? v.toString() : null;
    }

    /**
     * Traduit le statut Oracle interne de TRAINING_REQUESTS en valeur compréhensible
     * par le frontend Angular.
     *
     * @param s valeur brute du champ STATUS en base Oracle
     * @return code statut normalisé pour le frontend Angular
     */
    private String mapStatut(String s) {
        if (s == null) return "EN_ATTENTE";
        return switch (s) {
            case "APPROUVE_CHEF" -> "APPROUVE_CHEF";
            case "APPROUVE_RH"   -> "APPROUVE_RH";
            case "PLANIFIEE"     -> "PLANIFIEE";
            case "EN_COURS"      -> "EN_COURS";
            case "COMPLETEE"     -> "COMPLETEE";
            case "REFUSE"        -> "REJETE_CHEF";
            case "REJETE_RH"     -> "REJETE_RH";
            case "ANNULE"        -> "ANNULEE";
            default              -> "EN_ATTENTE";
        };
    }
}
