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
import java.util.Objects;

/**
 * Service métier central de {@code taches-service}, orchestrant toutes les opérations
 * sur les tâches Kanban et leur cycle de vie.
 * <p>
 * {@code @Service} : déclare ce bean comme service Spring géré par le conteneur.
 * {@code @RequiredArgsConstructor} : génère un constructeur injectant toutes les dépendances finales.
 * <p>
 * Responsabilités :
 * <ul>
 *   <li>CRUD complet des tâches (création, lecture, modification, suppression)</li>
 *   <li>Résolution des identifiants depuis le JWT (employee_id depuis sub/email)</li>
 *   <li>Délégation aux appels Feign vers projets-service pour les données de projets</li>
 *   <li>Déclenchement des notifications Kafka via {@link TacheNotificationProducer}</li>
 *   <li>Mapping entre les entités Oracle et les DTOs Angular</li>
 * </ul>
 * <p>
 * Principe d'architecture microservices :
 * <ul>
 *   <li>{@code taches-service} est propriétaire exclusif de la table TASKS</li>
 *   <li>{@code projets-service} est propriétaire de PROJECTS + PROJECT_MEMBERS</li>
 *   <li>Aucun JPA croisé entre services — toutes les données de projets transitent par Feign</li>
 * </ul>
 *
 * @since 1.0
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

    /**
     * Récupère les tâches visibles par le chef connecté, avec filtre optionnel par nom de projet.
     * <p>
     * Si {@code projetNom} est fourni, résout le projet via Feign et retourne uniquement
     * les tâches de ce projet. Sans filtre, retourne toutes les tâches de tous les projets
     * du chef (récupérés via Feign), triées par date de création décroissante.
     *
     * @param projetNom nom du projet servant de filtre (peut être null ou vide)
     * @param auth      le contexte d'authentification du chef connecté
     * @return liste des {@link TacheDTO} correspondant aux critères de filtrage
     */
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

    /**
     * Récupère toutes les tâches assignées à l'employé connecté.
     * <p>
     * Résout l'identifiant Oracle de l'employé depuis le JWT, puis charge les tâches
     * et résout en batch les noms de projets via Feign pour éviter les appels N+1.
     *
     * @param auth le contexte d'authentification de l'employé connecté
     * @return liste des {@link TacheDTO} assignées à l'employé, triées par date de création décroissante
     */
    @Transactional(readOnly = true)
    public List<TacheDTO> getTachesEmploye(Authentication auth) {
        Long empId = resolveEmployeeId(auth);
        log.info("[Tache] getTachesEmploye | empId={}", empId);
        List<Task> tasks = tacheRepo.findByAssignedToOrderByCreatedAtDesc(empId);
        Map<Long, String> projetNoms = resolveProjetNoms(tasks);
        return tasks.stream()
                .map(t -> toTacheDTOAvecNom(t, projetNoms.get(t.getProjectId())))
                .collect(Collectors.toList());
    }

    /**
     * Récupère les tâches actives (non terminées, non bloquées) de l'employé connecté,
     * triées par date d'échéance croissante.
     * <p>
     * Utilisé pour le widget de tableau de bord Angular affichant les tâches urgentes.
     * Les noms de projets sont résolus en batch via Feign.
     *
     * @param auth le contexte d'authentification de l'employé connecté
     * @return liste des {@link TacheDTO} actives de l'employé, triées par échéance
     */
    @Transactional(readOnly = true)
    public List<TacheDTO> getTachesActives(Authentication auth) {
        Long empId = resolveEmployeeId(auth);
        List<Task> tasks = tacheRepo.findActivesForEmployee(empId);
        Map<Long, String> projetNoms = resolveProjetNoms(tasks);
        return tasks.stream()
                .map(t -> toTacheDTOAvecNom(t, projetNoms.get(t.getProjectId())))
                .collect(Collectors.toList());
    }

    /* ═══════════════════════════════════════════════════════
       CRÉATION — POST /api/affectation/taches
       ═══════════════════════════════════════════════════════ */

    /**
     * Crée une nouvelle tâche en base Oracle et déclenche les notifications d'assignation.
     * <p>
     * Résout les identifiants Oracle (créateur, projet, assigné) depuis le JWT et le DTO.
     * Persiste la tâche, récupère le projet via Feign pour les notifications et le DTO retourné.
     * Si un assigné est défini, envoie une notification Kafka de manière asynchrone.
     *
     * @param request le DTO de création de tâche contenant les données saisies par le chef
     * @param auth    le contexte d'authentification identifiant le créateur (TASKS.created_by)
     * @return le {@link TacheDTO} de la tâche nouvellement créée
     * @throws IllegalArgumentException si le projet référencé est introuvable dans projets-service
     */
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

        return toTacheDTO(task, projet);
    }

    /* ═══════════════════════════════════════════════════════
       MODIFICATION — PUT /api/affectation/taches/{id}
       ═══════════════════════════════════════════════════════ */

    /**
     * Met à jour une tâche existante avec les nouvelles valeurs fournies par le chef.
     * <p>
     * Seuls les champs non-null du {@code request} sont appliqués (mise à jour partielle).
     * Si l'assigné change, une notification de réassignation est envoyée via Kafka.
     * Si l'assigné reste le même, une notification de modification est envoyée.
     *
     * @param taskId  identifiant Oracle de la tâche à modifier (TASKS.task_id)
     * @param request le DTO contenant les nouvelles valeurs (les champs null sont ignorés)
     * @param auth    le contexte d'authentification du chef effectuant la modification
     * @return le {@link TacheDTO} mis à jour
     * @throws IllegalArgumentException si la tâche n'existe pas pour l'identifiant fourni
     */
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

    /**
     * Met à jour partiellement le statut et/ou la progression d'une tâche (drag &amp; drop Kanban).
     * <p>
     * Vérifie que l'employé est bien l'assigné de la tâche (sauf pour les rôles Chef/Admin/RH).
     * Calcule automatiquement la progression si elle n'est pas fournie : 0 pour A_FAIRE, 100 pour TERMINE.
     * Si le statut change et que l'acteur n'est pas le chef du projet, envoie une notification Kafka.
     *
     * @param taskId  identifiant Oracle de la tâche à mettre à jour (TASKS.task_id)
     * @param request le DTO contenant le nouveau statut et la progression optionnelle
     * @param auth    le contexte d'authentification de l'utilisateur effectuant la mise à jour
     * @return le {@link TacheDTO} mis à jour (sans enrichissement Feign pour minimiser la latence)
     * @throws IllegalArgumentException si la tâche n'existe pas
     * @throws SecurityException        si l'employé tente de modifier une tâche qui ne lui est pas assignée
     */
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
            // Skip notifying the chef if they are the one who changed the status
            boolean changedByChef = projet != null && empId.equals(projet.getCreatedBy());
            if (!changedByChef) {
                notifProducer.notifierStatutChange(task, ancienStatut, projet);
            }
        }

        return toTacheDTOSansProjet(task);
    }

    /**
     * Met à jour le statut d'une tâche via un appel PUT explicite.
     * <p>
     * Délègue en interne à {@link #patchStatut} qui applique la même logique
     * de validation et de notification.
     *
     * @param taskId  identifiant Oracle de la tâche (TASKS.task_id)
     * @param request le DTO contenant le nouveau statut (champ {@code statut} obligatoire)
     * @param auth    le contexte d'authentification de l'utilisateur
     * @return le {@link TacheDTO} mis à jour
     * @throws IllegalArgumentException si la tâche n'existe pas
     * @throws SecurityException        si l'employé tente de modifier une tâche qui ne lui est pas assignée
     */
    @Transactional
    public TacheDTO updateStatut(Long taskId, StatutUpdateRequest request, Authentication auth) {
        return patchStatut(taskId, request, auth);
    }

    /* ═══════════════════════════════════════════════════════
       SUPPRESSION — DELETE /api/affectation/taches/{id}
       ═══════════════════════════════════════════════════════ */

    /**
     * Supprime définitivement une tâche de la base Oracle.
     * <p>
     * Un Admin/RH peut supprimer n'importe quelle tâche.
     * Un Chef ne peut supprimer que les tâches de ses propres projets
     * (vérifié via Feign : l'identifiant du chef doit correspondre à {@code projet.createdBy}).
     *
     * @param taskId identifiant Oracle de la tâche à supprimer (TASKS.task_id)
     * @param auth   le contexte d'authentification de l'utilisateur effectuant la suppression
     * @throws IllegalArgumentException si la tâche n'existe pas pour l'identifiant fourni
     * @throws SecurityException        si le Chef tente de supprimer une tâche d'un projet dont il n'est pas le créateur
     */
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
     * Batch-résolution des noms de projet pour une liste de tâches.
     * Une seule boucle Feign sur les IDs uniques — évite N appels individuels.
     */
    private Map<Long, String> resolveProjetNoms(List<Task> tasks) {
        Map<Long, String> noms = new HashMap<>();
        tasks.stream()
                .map(Task::getProjectId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(pid -> {
                    ProjetDTO p = fetchProjet(pid);
                    if (p != null) noms.put(pid, p.getNom());
                });
        return noms;
    }

    /** Mapper allégé avec nom de projet pré-résolu (évite un appel Feign par tâche). */
    private TacheDTO toTacheDTOAvecNom(Task t, String projetNom) {
        String assigneNom    = t.getAssignedTo() != null
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

    /**
     * Résout l'identifiant Oracle de l'employé connecté depuis le JWT.
     * <p>
     * Stratégie de résolution par ordre de priorité :
     * <ol>
     *   <li>Claim {@code employee_id} dans le JWT (Number ou String)</li>
     *   <li>Recherche en base via le claim {@code sub} (UUID Keycloak)</li>
     *   <li>Recherche en base via le claim {@code email}</li>
     * </ol>
     *
     * @param auth le contexte d'authentification contenant le JWT
     * @return l'identifiant Oracle de l'employé (EMPLOYEES.employee_id)
     * @throws IllegalStateException si le JWT est absent ou si l'employé est introuvable
     */
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

    /**
     * Résout l'identifiant Oracle du projet depuis le DTO de requête.
     * <p>
     * Si {@code projetId} est fourni directement, il est utilisé en priorité.
     * Sinon, le nom du projet est résolu via un appel Feign à projets-service.
     *
     * @param req le DTO de création/modification de tâche
     * @return l'identifiant Oracle du projet (PROJECTS.project_id), ou null si non spécifié
     * @throws IllegalArgumentException si le nom de projet fourni est introuvable dans projets-service
     */
    private Long resolveProjectId(TacheRequest req) {
        if (req.getProjetId() != null) return req.getProjetId();
        if (req.getProjet() != null && !req.getProjet().isBlank()) {
            ProjetDTO p = projetClient.findByName(req.getProjet()).orElse(null);
            if (p == null) throw new IllegalArgumentException("Projet introuvable : " + req.getProjet());
            return p.getId();
        }
        return null;
    }

    /**
     * Résout l'identifiant Oracle de l'employé assigné depuis le DTO de requête.
     * <p>
     * Si {@code assigneId} est fourni directement, il est utilisé en priorité.
     * Sinon, le nom complet ({@code assigneA}) est résolu via une requête native sur EMPLOYEES.
     *
     * @param req le DTO de création/modification de tâche
     * @return l'identifiant Oracle de l'employé assigné, ou null si aucun assigné n'est spécifié
     */
    private Long resolveAssignedTo(TacheRequest req) {
        if (req.getAssigneId() != null) return req.getAssigneId();
        if (req.getAssigneA() != null && !req.getAssigneA().isBlank()) {
            return empRepo.findEmployeeIdByFullName(req.getAssigneA());
        }
        return null;
    }

    /**
     * Vérifie si l'utilisateur connecté possède un rôle Chef, RH ou Admin.
     * <p>
     * Utilisé pour les contrôles d'autorisation dans les opérations de modification
     * et de suppression des tâches.
     *
     * @param auth le contexte d'authentification (peut être null)
     * @return {@code true} si l'utilisateur a le rôle CHEF, RH ou ADMIN ; {@code false} sinon
     */
    private boolean isChefOrAdmin(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CHEF")
                        || a.getAuthority().equals("ROLE_RH")
                        || a.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Extrait le token JWT depuis le contexte d'authentification Spring Security.
     *
     * @param auth le contexte d'authentification
     * @return le {@link Jwt} si l'authentification est de type {@link JwtAuthenticationToken}, null sinon
     */
    private Jwt extractJwt(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken j) return j.getToken();
        return null;
    }

    /* ── Convertisseurs priorité / statut (inchangés) ── */

    /**
     * Convertit un label de priorité Angular en valeur Oracle compatible avec le CHECK de la table TASKS.
     * <p>
     * Mapping : haute/high → HAUTE, moyenne/medium → NORMALE, basse/low/faible → FAIBLE, critique → CRITIQUE.
     *
     * @param ap label de priorité Angular (insensible à la casse)
     * @return valeur Oracle correspondante, ou {@code NORMALE} si null, ou la valeur en majuscules si non reconnue
     */
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

    /**
     * Convertit une priorité Oracle en label Angular lisible pour l'interface utilisateur.
     * <p>
     * Mapping : HAUTE → Haute, NORMALE → Moyenne, FAIBLE → Basse, CRITIQUE → Haute.
     *
     * @param op valeur Oracle de la priorité (HAUTE, NORMALE, FAIBLE, CRITIQUE)
     * @return label Angular correspondant, ou "Moyenne" si null
     */
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

    /**
     * Retourne la couleur HEX associée à un label de priorité Angular.
     * <p>
     * Valeurs retournées : Haute → {@code #ff5370}, Basse → {@code #2ed8b6}, autres → {@code #FFB64D}.
     *
     * @param ap label Angular de la priorité (Haute, Basse, Moyenne, Critique)
     * @return code couleur HEX attendu par Angular pour la coloration des badges de priorité
     */
    private String toPrioriteColor(String ap) {
        return switch (ap) {
            case "Haute" -> "#ff5370";
            case "Basse" -> "#2ed8b6";
            default      -> "#FFB64D";
        };
    }

    /**
     * Convertit un statut Angular en valeur Oracle compatible avec le CHECK de la table TASKS.
     * <p>
     * Mapping : TERMINEE/TERMINÉ/TERMINE → TERMINE, EN_COURS → EN_COURS,
     * EN_REVUE → EN_REVUE, BLOQUE/BLOQUÉ → BLOQUE, autres → A_FAIRE.
     *
     * @param as statut Angular envoyé par le frontend (insensible à la casse)
     * @return valeur Oracle correspondante, ou {@code A_FAIRE} si null ou non reconnu
     */
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

    /**
     * Convertit un statut Oracle en valeur attendue par le Kanban Angular.
     * <p>
     * Mapping : TERMINE → TERMINEE, EN_REVUE → EN_COURS, BLOQUE → A_FAIRE, autres → valeur inchangée.
     * Cette simplification réduit les 5 statuts Oracle aux 3 colonnes du Kanban Angular.
     *
     * @param os valeur Oracle du statut (A_FAIRE, EN_COURS, EN_REVUE, TERMINE, BLOQUE)
     * @return valeur Angular correspondante pour le Kanban employé
     */
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