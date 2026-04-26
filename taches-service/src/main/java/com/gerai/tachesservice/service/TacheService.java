package com.gerai.tachesservice.service;

import com.gerai.tachesservice.client.ProjetClient;
import com.gerai.tachesservice.dto.*;
import com.gerai.tachesservice.entity.Task;
import com.gerai.tachesservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TacheService
 *
 * SUPPRESSIONS par rapport à la version précédente :
 *   ✗ ProjetRepository         → remplacé par ProjetClient (Feign)
 *   ✗ ProjectMemberRepository  → remplacé par ProjetClient (Feign)
 *   ✗ entity/Project.java      → le DTO ProjetDTO suffit
 *   ✗ entity/ProjectMember.java→ le DTO ProjetDTO.MembreDTO suffit
 *
 * CONSERVÉ :
 *   ✓ TacheRepository          → propre à ce service (TASKS Oracle)
 *   ✓ EmployeeQueryRepository  → résolution JWT → employee_id
 *   ✓ TacheNotificationProducer→ Kafka vers notification-service
 *   ✓ entity/Task.java         → entité TASKS (propre à ce service)
 *
 * PRINCIPE :
 *   taches-service est propriétaire de la table TASKS.
 *   projets-service est propriétaire de PROJECTS + PROJECT_MEMBERS.
 *   Chaque service expose ses données via API REST — pas de JPA croisé.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TacheService {

    /* ── Repo propre à taches-service ──────────────── */
    private final TacheRepository              tacheRepo;
    private final EmployeeQueryRepository      empRepo;

    /* ── Client Feign vers projets-service ──────────── */
    private final ProjetClient                 projetClient;

    /* ── Kafka ──────────────────────────────────────── */
    private final TacheNotificationProducer    notifProducer;

    /* ═══════════════════════════════════════════════════════
       PROJETS — délégation totale à projets-service
       ═══════════════════════════════════════════════════════ */

    /**
     * GET /api/affectation/projets
     * Délègue à projets-service — plus de ProjetRepository local.
     */
    @Transactional(readOnly = true)
    public List<ProjetDTO> getProjetsChef(Authentication auth) {
        log.info("[Tache] getProjetsChef | user={}", auth.getName());
        try {
            return projetClient.getProjetsChef();
        } catch (Exception e) {
            log.error("[Tache] Impossible de contacter projets-service : {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * GET /api/affectation/projets (RH/Admin)
     */
    @Transactional(readOnly = true)
    public List<ProjetDTO> getTousProjets() {
        try {
            return projetClient.getAllProjets();
        } catch (Exception e) {
            log.error("[Tache] Impossible de contacter projets-service : {}", e.getMessage());
            return List.of();
        }
    }

    /* ═══════════════════════════════════════════════════════
       TÂCHES — Espace Chef : GET /api/affectation/taches
       ═══════════════════════════════════════════════════════ */

    @Transactional(readOnly = true)
    public List<TacheDTO> getTachesChef(String projetNom, Authentication auth) {
        Long empId = resolveEmployeeId(auth);

        if (projetNom != null && !projetNom.isBlank()) {
            // Résoudre le nom du projet via Feign (plus de ProjetRepository local)
            ProjetDTO projet = projetClient.findByName(projetNom).orElse(null);
            if (projet == null) {
                log.warn("[Tache] Projet introuvable : '{}'", projetNom);
                return List.of();
            }
            return tacheRepo.findByProjectIdOrderByCreatedAtDesc(projet.getId())
                    .stream()
                    .map(t -> toTacheDTO(t, projet))
                    .collect(Collectors.toList());
        }

        // Sans filtre : récupérer les projets du chef via Feign
        List<ProjetDTO> mesProjets = getProjetsChef(auth);
        List<Long> projectIds = mesProjets.stream().map(ProjetDTO::getId).toList();

        // Map id → projetDTO pour enrichissement rapide
        Map<Long, ProjetDTO> projetMap = mesProjets.stream()
                .collect(Collectors.toMap(ProjetDTO::getId, p -> p));

        return tacheRepo.findAll().stream()
                .filter(t -> projectIds.contains(t.getProjectId()))
                .sorted(Comparator.comparing(Task::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(t -> toTacheDTO(t, projetMap.get(t.getProjectId())))
                .collect(Collectors.toList());
    }

    /* ═══════════════════════════════════════════════════════
       TÂCHES — Espace Employé : GET /api/taches
       ═══════════════════════════════════════════════════════ */

    @Transactional(readOnly = true)
    public List<TacheDTO> getTachesEmploye(Authentication auth) {
        Long empId = resolveEmployeeId(auth);
        log.info("[Tache] getTachesEmploye | empId={}", empId);
        return tacheRepo.findByAssignedToOrderByCreatedAtDesc(empId)
                .stream()
                .map(t -> toTacheDTOSansProjet(t))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TacheDTO> getTachesActives(Authentication auth) {
        Long empId = resolveEmployeeId(auth);
        return tacheRepo.findActivesForEmployee(empId)
                .stream()
                .map(t -> toTacheDTOSansProjet(t))
                .collect(Collectors.toList());
    }

    /* ═══════════════════════════════════════════════════════
       CRÉATION — POST /api/affectation/taches
       ═══════════════════════════════════════════════════════ */

    @Transactional
    public TacheDTO createTache(TacheRequest request, Authentication auth) {
        Long creatorId = resolveEmployeeId(auth);
        Long projectId = resolveProjectId(request);
        Long assignedTo = resolveAssignedTo(request);

        Task task = Task.builder()
                .projectId(projectId)
                .title(request.getTitre())
                .description(request.getDescription())
                .assignedTo(assignedTo)
                .createdBy(creatorId)
                .priority(toOraclePriority(request.getPriorite()))
                .status(request.getStatut() != null
                        ? toOracleStatut(request.getStatut()) : "A_FAIRE")
                .progressPct(request.getProgression() != null ? request.getProgression() : 0)
                .dueDate(request.getEcheance())
                .build();

        task = tacheRepo.save(task);
        log.info("[Tache] Créée | id={} | projet={} | assigné={}",
                task.getTaskId(), projectId, assignedTo);

        // Récupérer le projet via Feign pour les notifications et le DTO
        ProjetDTO projet = projectId != null ? fetchProjet(projectId) : null;

        if (assignedTo != null) {
            notifProducer.notifierAssignation(task, projet);
        }
        if (!creatorId.equals(assignedTo)) {
            notifProducer.notifierChefCreation(task, projet, creatorId);
        }

        return toTacheDTO(task, projet);
    }

    /* ═══════════════════════════════════════════════════════
       MODIFICATION — PUT /api/affectation/taches/{id}
       ═══════════════════════════════════════════════════════ */

    @Transactional
    public TacheDTO updateTache(Long taskId, TacheRequest request, Authentication auth) {
        Task task = tacheRepo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Tâche introuvable : " + taskId));

        Long ancienAssigne = task.getAssignedTo();

        if (request.getTitre()       != null) task.setTitle(request.getTitre());
        if (request.getDescription() != null) task.setDescription(request.getDescription());
        if (request.getEcheance()    != null) task.setDueDate(request.getEcheance());
        if (request.getPriorite()    != null) task.setPriority(toOraclePriority(request.getPriorite()));
        if (request.getStatut()      != null) task.setStatus(toOracleStatut(request.getStatut()));
        if (request.getProgression() != null) task.setProgressPct(request.getProgression());

        Long nouvelAssigne = resolveAssignedTo(request);
        if (nouvelAssigne != null) task.setAssignedTo(nouvelAssigne);

        task = tacheRepo.save(task);
        log.info("[Tache] Modifiée | id={}", taskId);

        ProjetDTO projet = fetchProjet(task.getProjectId());

        if (nouvelAssigne != null && !nouvelAssigne.equals(ancienAssigne)) {
            notifProducer.notifierReassignation(task, ancienAssigne, projet);
        } else if (ancienAssigne != null) {
            notifProducer.notifierChefModif(task, projet);
        }

        return toTacheDTO(task, projet);
    }

    /* ═══════════════════════════════════════════════════════
       PATCH STATUT — PATCH /api/taches/{id} (drag&drop)
       ═══════════════════════════════════════════════════════ */

    @Transactional
    public TacheDTO patchStatut(Long taskId, StatutUpdateRequest request, Authentication auth) {
        Long empId = resolveEmployeeId(auth);
        Task task = tacheRepo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Tâche introuvable : " + taskId));

        if (!isChefOrAdmin(auth) && !empId.equals(task.getAssignedTo())) {
            throw new SecurityException("Accès interdit : vous ne pouvez modifier que vos propres tâches");
        }

        String ancienStatut = task.getStatus();
        String oracleStatut = toOracleStatut(request.getStatut());
        task.setStatus(oracleStatut);

        if (request.getProgression() != null) {
            task.setProgressPct(request.getProgression());
        } else {
            task.setProgressPct(switch (oracleStatut) {
                case "A_FAIRE" -> 0;
                case "TERMINE" -> 100;
                default        -> task.getProgressPct();
            });
        }

        task = tacheRepo.save(task);
        log.info("[Tache] Statut PATCH | id={} | {} → {}", taskId, ancienStatut, oracleStatut);

        if (!oracleStatut.equals(ancienStatut)) {
            ProjetDTO projet = fetchProjet(task.getProjectId());
            notifProducer.notifierStatutChange(task, ancienStatut, projet);
        }

        return toTacheDTOSansProjet(task);
    }

    @Transactional
    public TacheDTO updateStatut(Long taskId, StatutUpdateRequest request, Authentication auth) {
        return patchStatut(taskId, request, auth);
    }

    /* ═══════════════════════════════════════════════════════
       SUPPRESSION — DELETE /api/affectation/taches/{id}
       ═══════════════════════════════════════════════════════ */

    @Transactional
    public void deleteTache(Long taskId, Authentication auth) {
        Task task = tacheRepo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Tâche introuvable : " + taskId));

        Long empId = resolveEmployeeId(auth);
        if (!isChefOrAdmin(auth)) {
            ProjetDTO projet = fetchProjet(task.getProjectId());
            if (projet == null || !empId.equals(projet.getCreatedBy())) {
                throw new SecurityException("Seul le Chef du projet peut supprimer cette tâche");
            }
        }

        tacheRepo.deleteById(taskId);
        log.info("[Tache] Supprimée | id={}", taskId);
    }

    /* ═══════════════════════════════════════════════════════
       HELPER FEIGN — récupération projet avec fallback
       ═══════════════════════════════════════════════════════ */

    /**
     * Récupère un ProjetDTO via Feign.
     * Retourne null si projets-service est indisponible (circuit breaker manuel).
     * Aucune exception ne remonte vers le client HTTP.
     */
    private ProjetDTO fetchProjet(Long projectId) {
        if (projectId == null) return null;
        try {
            return projetClient.getProjetById(projectId);
        } catch (Exception e) {
            log.warn("[Tache] Projet {} inaccessible via Feign : {}", projectId, e.getMessage());
            return null;
        }
    }

    /* ═══════════════════════════════════════════════════════
       MAPPERS
       ═══════════════════════════════════════════════════════ */

    /**
     * Mapper complet avec enrichissement projet via Feign.
     * Utilisé dans l'espace Chef où le nom du projet est affiché.
     */
    private TacheDTO toTacheDTO(Task t, ProjetDTO projet) {
        String projetNom = projet != null ? projet.getNom() : resolveProjetNomLazy(t.getProjectId());
        String assigneNom = t.getAssignedTo() != null
                ? empRepo.findFullNameById(t.getAssignedTo()) : null;

        String prioriteLabel = toAngularPriorite(t.getPriority());

        return TacheDTO.builder()
                .id(t.getTaskId())
                .titre(t.getTitle())
                .projet(projetNom)
                .projetId(t.getProjectId())
                .priorite(prioriteLabel)
                .prioriteColor(toPrioriteColor(prioriteLabel))
                .statut(toAngularStatut(t.getStatus()))
                .echeance(t.getDueDate())
                .dateCreation(t.getCreatedAt())
                .assigneA(assigneNom)
                .assigneId(t.getAssignedTo())
                .creePar(t.getCreatedBy())
                .progression(t.getProgressPct())
                .description(t.getDescription())
                .build();
    }

    /**
     * Mapper allégé sans appel Feign pour projet.
     * Utilisé dans l'espace Employé (Kanban) où le nom du projet
     * n'est pas critique et l'appel Feign ajouterait de la latence.
     */
    private TacheDTO toTacheDTOSansProjet(Task t) {
        String assigneNom = t.getAssignedTo() != null
                ? empRepo.findFullNameById(t.getAssignedTo()) : null;
        String prioriteLabel = toAngularPriorite(t.getPriority());

        return TacheDTO.builder()
                .id(t.getTaskId())
                .titre(t.getTitle())
                .projetId(t.getProjectId())
                .priorite(prioriteLabel)
                .prioriteColor(toPrioriteColor(prioriteLabel))
                .statut(toAngularStatut(t.getStatus()))
                .echeance(t.getDueDate())
                .dateCreation(t.getCreatedAt())
                .assigneA(assigneNom)
                .assigneId(t.getAssignedTo())
                .creePar(t.getCreatedBy())
                .progression(t.getProgressPct())
                .description(t.getDescription())
                .build();
    }

    /**
     * Résolution paresseuse du nom de projet via Feign, uniquement si nécessaire.
     */
    private String resolveProjetNomLazy(Long projectId) {
        if (projectId == null) return null;
        ProjetDTO p = fetchProjet(projectId);
        return p != null ? p.getNom() : null;
    }

    /* ═══════════════════════════════════════════════════════
       RÉSOLUTION D'IDENTITÉ
       ═══════════════════════════════════════════════════════ */

    private Long resolveEmployeeId(Authentication auth) {
        Jwt jwt = extractJwt(auth);
        if (jwt == null) throw new IllegalStateException("JWT manquant");

        Object claim = jwt.getClaim("employee_id");
        if (claim instanceof Number n) return n.longValue();
        if (claim instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }

        String sub = jwt.getSubject();
        Long empId = empRepo.findEmployeeIdBySub(sub);
        if (empId != null) return empId;

        String email = jwt.getClaimAsString("email");
        empId = empRepo.findEmployeeIdByEmail(email);
        if (empId != null) return empId;

        throw new IllegalStateException("Employé introuvable pour sub=" + sub);
    }

    private Long resolveProjectId(TacheRequest req) {
        if (req.getProjetId() != null) return req.getProjetId();
        if (req.getProjet() != null && !req.getProjet().isBlank()) {
            ProjetDTO p = projetClient.findByName(req.getProjet()).orElse(null);
            if (p == null) throw new IllegalArgumentException("Projet introuvable : " + req.getProjet());
            return p.getId();
        }
        return null;
    }

    private Long resolveAssignedTo(TacheRequest req) {
        if (req.getAssigneId() != null) return req.getAssigneId();
        if (req.getAssigneA() != null && !req.getAssigneA().isBlank()) {
            return empRepo.findEmployeeIdByFullName(req.getAssigneA());
        }
        return null;
    }

    private boolean isChefOrAdmin(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CHEF")
                        || a.getAuthority().equals("ROLE_RH")
                        || a.getAuthority().equals("ROLE_ADMIN"));
    }

    private Jwt extractJwt(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken j) return j.getToken();
        return null;
    }

    /* ── Convertisseurs priorité / statut (inchangés) ── */

    private String toOraclePriority(String ap) {
        if (ap == null) return "NORMALE";
        return switch (ap.toLowerCase()) {
            case "haute","high"           -> "HAUTE";
            case "moyenne","medium"       -> "NORMALE";
            case "basse","low","faible"   -> "FAIBLE";
            case "critique","critical"    -> "CRITIQUE";
            default                       -> ap.toUpperCase();
        };
    }

    private String toAngularPriorite(String op) {
        if (op == null) return "Moyenne";
        return switch (op.toUpperCase()) {
            case "HAUTE"    -> "Haute";
            case "NORMALE"  -> "Moyenne";
            case "FAIBLE"   -> "Basse";
            case "CRITIQUE" -> "Haute";
            default         -> op;
        };
    }

    private String toPrioriteColor(String ap) {
        return switch (ap) {
            case "Haute" -> "#ff5370";
            case "Basse" -> "#2ed8b6";
            default      -> "#FFB64D";
        };
    }

    private String toOracleStatut(String as) {
        if (as == null) return "A_FAIRE";
        return switch (as.toUpperCase()) {
            case "TERMINEE","TERMINÉ","TERMINE" -> "TERMINE";
            case "EN_COURS"                     -> "EN_COURS";
            case "EN_REVUE"                     -> "EN_REVUE";
            case "BLOQUE","BLOQUÉ"              -> "BLOQUE";
            default                             -> "A_FAIRE";
        };
    }

    private String toAngularStatut(String os) {
        if (os == null) return "A_FAIRE";
        return switch (os.toUpperCase()) {
            case "TERMINE"  -> "TERMINEE";
            case "EN_REVUE" -> "EN_COURS";
            case "BLOQUE"   -> "A_FAIRE";
            default         -> os;
        };
    }
}