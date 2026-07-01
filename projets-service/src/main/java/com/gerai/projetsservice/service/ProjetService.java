package com.gerai.projetsservice.service;

import com.gerai.projetsservice.config.JwtHelperInterface;
import com.gerai.projetsservice.dto.*;
import com.gerai.projetsservice.model.*;
import com.gerai.projetsservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service principal de gestion des projets, tâches et évaluations de performance.
 * <p>
 * Centralise la logique métier pour :
 * <ul>
 *   <li>Création, modification, suppression et consultation des projets.</li>
 *   <li>Gestion des membres d'équipe (ajout, règle un-projet-par-MEMBRE).</li>
 *   <li>Gestion du cycle de vie des tâches (création, assignation, avancement, bascule TERMINE).</li>
 *   <li>Calcul du tableau de bord agrégé et des indicateurs de performance chef.</li>
 *   <li>Création et consultation des évaluations de performance ({@link PerformanceEval}).</li>
 *   <li>Résolution des données employés via {@link EmployeService} (REST + repli Oracle).</li>
 *   <li>Publication de notifications Kafka via {@link ProjectNotificationService}.</li>
 * </ul>
 * </p>
 * <p>
 * {@code @Service} : composant Spring géré par le conteneur IoC.<br>
 * {@code @Slf4j} : journalisation SLF4J via Lombok.<br>
 * {@code @RequiredArgsConstructor} : injection des dépendances par constructeur (champs {@code final}).
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjetService {

    /** Repository JPA pour les projets (table {@code PROJECTS}). */
    private final ProjectRepository         projectRepo;
    /** Repository JPA pour les membres d'équipe (table {@code PROJECT_MEMBERS}). */
    private final ProjectMemberRepository   memberRepo;
    /** Repository JPA pour les tâches (table {@code PROJECT_TASKS}). */
    private final TaskRepository            taskRepo;
    /** Repository JPA pour les commentaires de tâches (table {@code TASK_COMMENTS}). */
    private final TaskCommentRepository     commentRepo;
    /** Repository JPA pour les évaluations de performance. */
    private final PerformanceEvalRepository evalRepo;
    /** Helper JWT pour l'extraction de l'identifiant et des rôles de l'utilisateur courant. */
    private final JwtHelperInterface         jwt;
    /** Service de résolution des informations des employés (REST + repli Oracle). */
    private final EmployeService            employeService;
    /** Service de publication des notifications Kafka liées aux projets. */
    private final ProjectNotificationService notifService;
    /** Template JDBC pour les requêtes SQL natives complexes (liste des employés, etc.). */
    private final JdbcTemplate              jdbc;

    /**
     * Retourne la liste des projets créés par le chef authentifié, triés par date de création décroissante.
     *
     * @param auth jeton d'authentification Spring Security du chef de projet
     * @return liste de {@link ProjetDTO} appartenant au chef, éventuellement vide
     */
    @Transactional(readOnly = true)
    public List<ProjetDTO> getProjetsChef(Authentication auth) {
        Long chefId = jwt.getEmployeeId(auth);
        return projectRepo.findByCreatedByOrderByCreatedAtDesc(chefId)
                .stream().map(this::toProjetDTO).collect(Collectors.toList());
    }

    /**
     * Crée un nouveau projet et y ajoute automatiquement le chef et les membres demandés.
     * <p>
     * Le créateur est inscrit avec le rôle {@code CHEF}. Chaque membre supplémentaire
     * reçoit une notification Kafka via {@link ProjectNotificationService#notifierMembreAjoute}.
     * </p>
     *
     * @param req  données de création du projet (nom, description, membres, dates, etc.)
     * @param auth jeton d'authentification Spring Security du chef de projet
     * @return le {@link ProjetDTO} du projet créé avec ses membres et tâches
     */
    @Transactional
    public ProjetDTO createProjet(CreateProjetRequest req, Authentication auth) {
        Long chefId = jwt.getEmployeeId(auth);
        Project project = Project.builder()
                .name(req.getNom()).description(req.getDescription()).code(req.getCode())
                .createdBy(chefId).startDate(req.getDateDebut()).endDate(req.getDatefin())
                .status(req.getStatut() != null ? req.getStatut() : "EN_COURS")
                .priority(req.getPriority() != null ? req.getPriority() : "NORMALE")
                .progressPct(req.getProgression() != null ? req.getProgression() : 0)
                .build();
        project = projectRepo.save(project);
        addMembre(project, chefId, "CHEF");
        List<Long> membresAjoutes = new ArrayList<>();
        if (req.getMembreIds() != null) {
            for (Long empId : req.getMembreIds()) {
                if (!empId.equals(chefId)) { addMembre(project, empId, "MEMBRE"); membresAjoutes.add(empId); }
            }
        }
        log.info("[Projet] Créé id={} par chef={}", project.getProjectId(), chefId);
        final Project saved = project;
        for (Long empId : membresAjoutes) notifService.notifierMembreAjoute(saved, empId, chefId);
        return toProjetDTO(project);
    }

    /**
     * Met à jour un projet existant appartenant au chef authentifié.
     * <p>
     * Gère la synchronisation des membres (désactivation des anciens, ajout des nouveaux)
     * et publie des notifications Kafka selon les changements de statut ou d'avancement.
     * </p>
     *
     * @param projectId identifiant du projet à mettre à jour
     * @param req       données de mise à jour (champs {@code null} ignorés)
     * @param auth      jeton d'authentification Spring Security du chef de projet
     * @return le {@link ProjetDTO} mis à jour
     * @throws SecurityException      si le projet n'appartient pas au chef authentifié
     * @throws NoSuchElementException si le projet est introuvable
     */
    @Transactional
    public ProjetDTO updateProjet(Long projectId, UpdateProjetRequest req, Authentication auth) {
        Long chefId = jwt.getEmployeeId(auth);
        Project project = findProjetOwnedByChef(projectId, chefId);
        boolean statusChange = req.getStatut() != null && !req.getStatut().equals(project.getStatus());
        if (req.getNom()         != null) project.setName(req.getNom());
        if (req.getDescription() != null) project.setDescription(req.getDescription());
        if (req.getDateDebut()   != null) project.setStartDate(req.getDateDebut());
        if (req.getDatefin()     != null) project.setEndDate(req.getDatefin());
        if (req.getStatut()      != null) project.setStatus(req.getStatut());
        if (req.getProgression() != null) project.setProgressPct(req.getProgression());
        List<Long> nouveauxMembres = new ArrayList<>();
        if (req.getMembreIds() != null) {
            memberRepo.findByProject_ProjectId(projectId).forEach(m -> {
                if (!"CHEF".equals(m.getRole())) { m.setIsActive(0); memberRepo.save(m); }
            });
            for (Long empId : req.getMembreIds()) {
                if (!empId.equals(chefId))
                    memberRepo.findByProject_ProjectIdAndEmployeeId(projectId, empId)
                            .ifPresentOrElse(
                                    m -> { m.setIsActive(1); memberRepo.save(m); },
                                    () -> { addMembre(project, empId, "MEMBRE"); nouveauxMembres.add(empId); });
            }
        }
        Project saved = projectRepo.save(project);
        for (Long empId : nouveauxMembres) notifService.notifierMembreAjoute(saved, empId, chefId);
        if (statusChange && "TERMINE".equals(req.getStatut())) {
            List<Long> tous = memberRepo.findActiveByProjectId(projectId).stream().map(ProjectMember::getEmployeeId).collect(Collectors.toList());
            notifService.notifierProjetTermine(saved, chefId, tous);
        } else if (statusChange || req.getProgression() != null) {
            List<Long> membres = memberRepo.findActiveByProjectId(projectId).stream().map(ProjectMember::getEmployeeId).collect(Collectors.toList());
            notifService.notifierProjetModifie(saved, chefId, membres);
        }
        return toProjetDTO(saved);
    }

    /**
     * Supprime un projet appartenant au chef authentifié (suppression en cascade des membres et tâches).
     *
     * @param projectId identifiant du projet à supprimer
     * @param auth      jeton d'authentification Spring Security du chef de projet
     * @throws SecurityException      si le projet n'appartient pas au chef authentifié
     * @throws NoSuchElementException si le projet est introuvable
     */
    @Transactional
    public void deleteProjet(Long projectId, Authentication auth) {
        Long chefId = jwt.getEmployeeId(auth);
        projectRepo.delete(findProjetOwnedByChef(projectId, chefId));
    }

    /**
     * Crée une nouvelle tâche dans un projet appartenant au chef authentifié.
     * <p>
     * Si un employé est assigné, une notification Kafka lui est envoyée via
     * {@link ProjectNotificationService#notifierTacheAssignee}.
     * </p>
     *
     * @param req  données de création de la tâche (titre, description, assigné, échéance, etc.)
     * @param auth jeton d'authentification Spring Security du chef de projet
     * @return le {@link TacheDTO} de la tâche créée
     * @throws SecurityException      si le projet cible n'appartient pas au chef authentifié
     * @throws NoSuchElementException si le projet ou la tâche parente est introuvable
     */
    @Transactional
    public TacheDTO createTache(CreateTacheRequest req, Authentication auth) {
        Long chefId = jwt.getEmployeeId(auth);
        Project project = findProjetOwnedByChef(req.getProjectId(), chefId);
        Task task = Task.builder().project(project).title(req.getTitre())
                .description(req.getDescription()).assignedTo(req.getAssignedTo()).createdBy(chefId)
                .priority(req.getPrioritize() != null ? req.getPrioritize() : "NORMALE")
                .dueDate(req.getEcheance()).estimatedHours(req.getEstimatedHours()).status("A_FAIRE").build();
        if (req.getParentTaskId() != null) taskRepo.findById(req.getParentTaskId()).ifPresent(task::setParentTask);
        Task saved = taskRepo.save(task);
        if (saved.getAssignedTo() != null) notifService.notifierTacheAssignee(saved, chefId);
        return toTacheDTO(saved);
    }

    /**
     * Met à jour une tâche existante dans un projet appartenant au chef authentifié.
     * <p>
     * Si l'assigné change, une notification Kafka est publiée pour le nouvel assigné.
     * Les champs {@code null} dans {@code req} sont ignorés (mise à jour partielle).
     * </p>
     *
     * @param taskId identifiant de la tâche à mettre à jour
     * @param req    données de mise à jour (champs {@code null} ignorés)
     * @param auth   jeton d'authentification Spring Security du chef de projet
     * @return le {@link TacheDTO} mis à jour
     * @throws SecurityException      si le projet parent n'appartient pas au chef authentifié
     * @throws NoSuchElementException si la tâche est introuvable
     */
    @Transactional
    public TacheDTO updateTache(Long taskId, CreateTacheRequest req, Authentication auth) {
        Long chefId = jwt.getEmployeeId(auth);
        Task task = taskRepo.findById(taskId).orElseThrow(() -> new NoSuchElementException("Tâche introuvable"));
        findProjetOwnedByChef(task.getProject().getProjectId(), chefId);
        Long ancienAssigne = task.getAssignedTo();
        if (req.getTitre()          != null) task.setTitle(req.getTitre());
        if (req.getDescription()    != null) task.setDescription(req.getDescription());
        if (req.getAssignedTo()     != null) task.setAssignedTo(req.getAssignedTo());
        if (req.getPrioritize()     != null) task.setPriority(req.getPrioritize());
        if (req.getEcheance()       != null) task.setDueDate(req.getEcheance());
        if (req.getEstimatedHours() != null) task.setEstimatedHours(req.getEstimatedHours());
        Task saved = taskRepo.save(task);
        if (req.getAssignedTo() != null && !req.getAssignedTo().equals(ancienAssigne)) notifService.notifierTacheAssignee(saved, chefId);
        return toTacheDTO(saved);
    }

    /**
     * Assigne une tâche à un employé spécifique (opération dédiée, sans modifier les autres champs).
     * <p>
     * Si l'employé assigné change par rapport à l'ancien assigné, une notification Kafka est publiée.
     * </p>
     *
     * @param taskId     identifiant de la tâche à assigner
     * @param employeeId identifiant Oracle de l'employé à qui assigner la tâche
     * @param auth       jeton d'authentification Spring Security du chef de projet
     * @return le {@link TacheDTO} mis à jour avec le nouvel assigné
     * @throws SecurityException      si le projet parent n'appartient pas au chef authentifié
     * @throws NoSuchElementException si la tâche est introuvable
     */
    @Transactional
    public TacheDTO assignTache(Long taskId, Long employeeId, Authentication auth) {
        Long chefId = jwt.getEmployeeId(auth);
        Task task = taskRepo.findById(taskId).orElseThrow(() -> new NoSuchElementException("Tâche introuvable"));
        findProjetOwnedByChef(task.getProject().getProjectId(), chefId);
        Long ancienAssigne = task.getAssignedTo();
        task.setAssignedTo(employeeId);
        Task saved = taskRepo.save(task);
        if (!employeeId.equals(ancienAssigne)) notifService.notifierTacheAssignee(saved, chefId);
        return toTacheDTO(saved);
    }

    /**
     * Retourne la liste des projets dont l'employé authentifié est membre actif.
     *
     * @param auth jeton d'authentification Spring Security de l'employé
     * @return liste de {@link ProjetDTO} des projets de l'employé, éventuellement vide
     */
    @Transactional(readOnly = true)
    public List<ProjetDTO> getMesProjets(Authentication auth) {
        return projectRepo.findByMemberEmployeeId(jwt.getEmployeeId(auth))
                .stream().map(this::toProjetDTO).collect(Collectors.toList());
    }

    /**
     * Retourne les détails complets d'un projet si l'utilisateur y a accès.
     * <p>
     * L'accès est accordé aux administrateurs/RH, au chef créateur du projet,
     * et aux membres actifs du projet.
     * </p>
     *
     * @param projectId identifiant du projet à consulter
     * @param auth      jeton d'authentification Spring Security de l'utilisateur
     * @return le {@link ProjetDTO} du projet avec ses membres et tâches
     * @throws SecurityException      si l'utilisateur n'est pas membre ou admin/RH
     * @throws NoSuchElementException si le projet est introuvable
     */
    @Transactional(readOnly = true)
    public ProjetDTO getProjetById(Long projectId, Authentication auth) {
        Long empId = jwt.getEmployeeId(auth);
        Project project = projectRepo.findById(projectId).orElseThrow(() -> new NoSuchElementException("Projet introuvable"));
        boolean hasAccess = jwt.isAdminOrRh(auth) || project.getCreatedBy().equals(empId)
                || memberRepo.findByProject_ProjectIdAndEmployeeId(projectId, empId).isPresent();
        if (!hasAccess) throw new SecurityException("Accès interdit à ce projet");
        return toProjetDTO(project);
    }

    /**
     * Retourne toutes les tâches assignées à l'employé authentifié.
     *
     * @param auth jeton d'authentification Spring Security de l'employé
     * @return liste de {@link TacheDTO} assignées à l'employé, éventuellement vide
     */
    @Transactional(readOnly = true)
    public List<TacheDTO> getMesTaches(Authentication auth) {
        return taskRepo.findMesTaches(jwt.getEmployeeId(auth)).stream().map(this::toTacheDTO).collect(Collectors.toList());
    }

    /**
     * Bascule le statut d'une tâche entre {@code TERMINE} et {@code EN_COURS} pour l'assigné authentifié.
     * <p>
     * Met également à jour la progression du projet parent. Si la tâche passe à {@code TERMINE},
     * une notification Kafka est publiée au chef de projet.
     * </p>
     *
     * @param taskId identifiant de la tâche à basculer
     * @param auth   jeton d'authentification Spring Security de l'assigné
     * @return le {@link TacheDTO} avec le nouveau statut et la progression mise à jour
     * @throws SecurityException      si l'utilisateur n'est pas l'assigné de la tâche
     * @throws NoSuchElementException si la tâche est introuvable
     */
    @Transactional
    public TacheDTO toggleTache(Long taskId, Authentication auth) {
        Long empId = jwt.getEmployeeId(auth);
        Task task = taskRepo.findById(taskId).orElseThrow(() -> new NoSuchElementException("Tâche introuvable"));
        if (!empId.equals(task.getAssignedTo())) throw new SecurityException("Seul l'assigné peut modifier");
        String ancienStatut = task.getStatus();
        String newStatus = "TERMINE".equals(ancienStatut) ? "EN_COURS" : "TERMINE";
        task.setStatus(newStatus);
        if ("TERMINE".equals(newStatus)) task.setProgressPct(100);
        else if (task.getProgressPct() == 100) task.setProgressPct(50);
        updateProjetProgression(task.getProject().getProjectId());
        Task saved = taskRepo.save(task);
        if ("TERMINE".equals(newStatus) && !"TERMINE".equals(ancienStatut)) notifService.notifierTacheTerminee(saved, empId);
        return toTacheDTO(saved);
    }

    /**
     * Met à jour le pourcentage d'avancement d'une tâche et ajuste son statut automatiquement.
     * <p>
     * Règles d'ajustement du statut :
     * <ul>
     *   <li>{@code progressPct >= 100} → statut {@code TERMINE}</li>
     *   <li>{@code progressPct > 0} et statut actuel {@code A_FAIRE} → statut {@code EN_COURS}</li>
     * </ul>
     * La progression du projet parent est recalculée. Une notification Kafka est publiée
     * au chef si la tâche passe à {@code TERMINE}.
     * </p>
     *
     * @param taskId      identifiant de la tâche à mettre à jour
     * @param progressPct pourcentage d'avancement (borné entre 0 et 100)
     * @param auth        jeton d'authentification Spring Security de l'assigné
     * @return le {@link TacheDTO} mis à jour
     * @throws SecurityException      si l'utilisateur n'est pas l'assigné de la tâche
     * @throws NoSuchElementException si la tâche est introuvable
     */
    @Transactional
    public TacheDTO updateAvancement(Long taskId, Integer progressPct, Authentication auth) {
        Long empId = jwt.getEmployeeId(auth);
        Task task = taskRepo.findById(taskId).orElseThrow(() -> new NoSuchElementException("Tâche introuvable"));
        if (!empId.equals(task.getAssignedTo())) throw new SecurityException("Seul l'assigné peut modifier");
        String ancienStatut = task.getStatus();
        task.setProgressPct(Math.max(0, Math.min(100, progressPct)));
        if (progressPct >= 100) task.setStatus("TERMINE");
        else if (progressPct > 0 && "A_FAIRE".equals(task.getStatus())) task.setStatus("EN_COURS");
        updateProjetProgression(task.getProject().getProjectId());
        Task saved = taskRepo.save(task);
        if ("TERMINE".equals(saved.getStatus()) && !"TERMINE".equals(ancienStatut)) notifService.notifierTacheTerminee(saved, empId);
        return toTacheDTO(saved);
    }

    /**
     * Supprime une tâche par son identifiant.
     *
     * @param taskId identifiant de la tâche à supprimer
     * @param auth   jeton d'authentification Spring Security (non utilisé pour la vérification actuelle)
     * @throws IllegalArgumentException si la tâche est introuvable
     */
    @Transactional
    public void deleteTache(Long taskId, Authentication auth) {
        if (!taskRepo.existsById(taskId)) {
            throw new IllegalArgumentException("Tâche introuvable: " + taskId);
        }
        taskRepo.deleteById(taskId);
        log.info("[Tache] Supprimée id={}", taskId);
    }

    /**
     * Retourne toutes les tâches des projets créés par le chef authentifié, triées par date de création.
     *
     * @param auth jeton d'authentification Spring Security du chef de projet
     * @return liste plate de {@link TacheDTO} de tous les projets du chef
     */
    @Transactional(readOnly = true)
    public List<TacheDTO> getTachesChef(Authentication auth) {
        Long chefId = jwt.getEmployeeId(auth);
        return projectRepo.findByCreatedByOrderByCreatedAtDesc(chefId).stream()
                .flatMap(p -> taskRepo.findByProject_ProjectIdOrderByCreatedAtAsc(p.getProjectId()).stream())
                .map(this::toTacheDTO)
                .collect(Collectors.toList());
    }

    /**
     * Retourne tous les projets de la base de données (accès administrateur/RH).
     *
     * @return liste complète de {@link ProjetDTO}
     */
    @Transactional(readOnly = true)
    public List<ProjetDTO> getAllProjets() {
        return projectRepo.findAll().stream().map(this::toProjetDTO).collect(Collectors.toList());
    }

    /**
     * Calcule et retourne les indicateurs agrégés du tableau de bord des projets.
     * <p>
     * Agrège : nombre total de projets, répartition par statut, total de tâches,
     * tâches terminées et taux de complétion global (arrondi à 2 décimales).
     * </p>
     *
     * @return le {@link DashboardDTO} avec tous les indicateurs agrégés
     */
    @Transactional(readOnly = true)
    public DashboardDTO getDashboard() {
        long total = projectRepo.countAll(), taches = taskRepo.countAll(), tachesOk = taskRepo.countAllTerminees();
        return DashboardDTO.builder()
                .totalProjets(total).projetsEnCours(projectRepo.countByStatus("EN_COURS"))
                .projetsTermines(projectRepo.countByStatus("TERMINE")).projetsEnAttente(projectRepo.countByStatus("PLANIFIE"))
                .projetsEnPause(projectRepo.countByStatus("EN_PAUSE")).totalTaches(taches).tachesTerminees(tachesOk)
                .tauxCompletion(taches > 0 ? round2((tachesOk * 100.0) / taches) : 0).build();
    }

    /**
     * Crée une nouvelle évaluation de performance pour un employé, soumise par le chef authentifié.
     * <p>
     * L'évaluation est créée avec le statut {@code SOUMIS}. L'identifiant de l'évaluateur
     * est résolu automatiquement depuis le jeton JWT.
     * </p>
     *
     * @param req  données de l'évaluation (employeeId, année, trimestre, score, forces, axes d'amélioration)
     * @param auth jeton d'authentification Spring Security de l'évaluateur (chef)
     * @return le {@link PerformanceEvalDTO} de l'évaluation créée avec son identifiant généré
     */
    @Transactional
    public PerformanceEvalDTO createEval(PerformanceEvalDTO req, Authentication auth) {
        PerformanceEval eval = PerformanceEval.builder().employeeId(req.getEmployeeId()).evaluatorId(jwt.getEmployeeId(auth))
                .periodYear(req.getPeriodYear()).periodQuarter(req.getPeriodQuarter()).score(req.getScore())
                .strengths(req.getStrengths()).improvements(req.getImprovements()).comments(req.getComments()).status("SOUMIS").build();
        return toEvalDTO(evalRepo.save(eval));
    }

    /**
     * Retourne toutes les évaluations de performance (accès administrateur/RH).
     *
     * @return liste complète de {@link PerformanceEvalDTO}
     */
    @Transactional(readOnly = true)
    public List<PerformanceEvalDTO> getEvals() { return evalRepo.findAll().stream().map(this::toEvalDTO).collect(Collectors.toList()); }

    /**
     * Retourne la liste des employés actifs disponibles pour être ajoutés à un projet.
     * <p>
     * Exclut l'utilisateur authentifié lui-même et les employés de niveau
     * {@code EXECUTIVE} ou {@code MANAGER}. Enrichit chaque employé avec
     * le nom de son projet actuel (s'il en a un).
     * En cas d'erreur DB, retourne une liste vide sans lever d'exception.
     * </p>
     *
     * @param auth jeton d'authentification Spring Security de l'utilisateur appelant
     * @return liste de {@link EmployeDTO} des employés actifs éligibles
     */
    public List<EmployeDTO> getEmployes(Authentication auth) {
        Long excludeId;
        try {
            excludeId = jwt.getEmployeeId(auth);
        } catch (Exception e) {
            log.warn("[ProjetService] getEmployes: could not resolve caller ID — {}", e.getMessage());
            excludeId = 0L;
        }
        try {
            return jdbc.query(
                "SELECT e.EMPLOYEE_ID, e.FIRST_NAME, e.LAST_NAME, e.EMAIL, e.PHONE, " +
                "       e.PHOTO_URL, e.STATUS, TO_CHAR(e.HIRE_DATE, 'YYYY-MM-DD') AS HIRE_DATE_STR, " +
                "       d.NOM AS DEPT_NOM, " +
                "       p.TITLE AS POSITION_TITLE, " +
                "       pr.PROJECT_ID AS CURRENT_PROJECT_ID, " +
                "       pr.NAME       AS CURRENT_PROJECT_NOM " +
                "FROM GERAI.EMPLOYEES e " +
                "LEFT JOIN GERAI.ADMIN_DEPARTEMENTS d  ON e.DEPT_ID       = d.DEPT_ADMIN_ID " +
                "LEFT JOIN GERAI.POSITIONS          p  ON e.POSITION_ID   = p.POSITION_ID " +
                "LEFT JOIN PROJECT_MEMBERS          pm ON e.EMPLOYEE_ID   = pm.EMPLOYEE_ID AND pm.IS_ACTIVE = 1 " +
                "LEFT JOIN PROJECTS                 pr ON pm.PROJECT_ID   = pr.PROJECT_ID " +
                "WHERE e.STATUS = 'ACTIF' " +
                "  AND e.EMPLOYEE_ID != ? " +
                "  AND (p.POS_LEVEL IS NULL OR p.POS_LEVEL NOT IN ('EXECUTIVE', 'MANAGER')) " +
                "ORDER BY e.LAST_NAME, e.FIRST_NAME",
                new Object[]{ excludeId },
                (rs, i) -> {
                    long pid = rs.getLong("CURRENT_PROJECT_ID");
                    boolean noProject = rs.wasNull();
                    return EmployeDTO.builder()
                        .id(rs.getLong("EMPLOYEE_ID"))
                        .prenom(rs.getString("FIRST_NAME"))
                        .nom(rs.getString("LAST_NAME"))
                        .email(rs.getString("EMAIL"))
                        .telephone(rs.getString("PHONE"))
                        .photo(rs.getString("PHOTO_URL"))
                        .statut(rs.getString("STATUS"))
                        .dateEmbauche(rs.getString("HIRE_DATE_STR"))
                        .poste(Optional.ofNullable(rs.getString("POSITION_TITLE")).orElse(""))
                        .departement(rs.getString("DEPT_NOM"))
                        .nomComplet(rs.getString("FIRST_NAME") + " " + rs.getString("LAST_NAME"))
                        .projetId(noProject ? null : pid)
                        .projetNom(rs.getString("CURRENT_PROJECT_NOM"))
                        .build();
                }
            );
        } catch (Exception e) {
            log.warn("[ProjetService] getEmployes DB fallback vide : {}", e.getMessage());
            return List.of();
        }
    }

    private Map<Long, EmployeDTO> fetchEmployeMap(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Map<Long, EmployeDTO> result = new HashMap<>();
        for (Long id : ids) {
            result.put(id, employeService.getEmployeById(id));
        }
        return result;
    }

    /**
     * Calcule les indicateurs de performance du chef de projet authentifié.
     * <p>
     * Indicateurs calculés :
     * <ul>
     *   <li>Taux de livraison projet (% de projets TERMINE sur total).</li>
     *   <li>Satisfaction client (dérivée du taux de livraison, bornée à 5).</li>
     *   <li>Collaboration d'équipe (% de tâches TERMINE sur total des tâches des projets du chef).</li>
     *   <li>Qualité code (moyenne des scores d'évaluation reçus, bornée à 100).</li>
     *   <li>Temps de résolution de bugs (valeur fixe illustrative : 2,5 jours).</li>
     * </ul>
     * </p>
     *
     * @param auth jeton d'authentification Spring Security du chef de projet
     * @return le {@link PerformanceChefDTO} avec les indicateurs calculés
     */
    @Transactional(readOnly = true)
    public PerformanceChefDTO getPerformanceChef(Authentication auth) {
        Long chefId = jwt.getEmployeeId(auth);
        List<Project> projets = projectRepo.findByCreatedByOrderByCreatedAtDesc(chefId);

        long total = projets.size();
        long termines = projets.stream().filter(p -> "TERMINE".equals(p.getStatus())).count();
        double tauxLivraison = total > 0 ? round2((termines * 100.0) / total) : 0;

        long taches   = projets.stream().mapToLong(p -> taskRepo.countByProjectId(p.getProjectId())).sum();
        long tachesOk = projets.stream().mapToLong(p -> taskRepo.countTerminesByProjectId(p.getProjectId())).sum();
        double collaboration = taches > 0 ? round2((tachesOk * 100.0) / taches) : 0;

        double avgScore = evalRepo.findByEvaluatorIdOrderByCreatedAtDesc(chefId)
                .stream().mapToDouble(e -> e.getScore() != null ? e.getScore() : 0).average().orElse(80.0);

        return PerformanceChefDTO.builder()
                .tauxLivraisonProjet(tauxLivraison)
                .satisfactionClient(total > 0 ? Math.min(5.0, round2(tauxLivraison / 20.0)) : 4.0)
                .collaborationEquipe(collaboration)
                .qualiteCode(Math.min(100.0, avgScore))
                .tempsResolutionBugs(2.5)
                .build();
    }

    /**
     * Recherche un projet par son nom (insensible à la casse).
     * <p>
     * Point de consommation Feign depuis {@code taches-service} via {@code GET /api/projets/by-name}.
     * </p>
     *
     * @param nom  nom du projet à rechercher
     * @param auth jeton d'authentification Spring Security (transmis par Feign)
     * @return un {@link Optional} contenant le {@link ProjetDTO} si trouvé, vide sinon
     */
    @Transactional(readOnly = true)
    public Optional<ProjetDTO> findByName(String nom, Authentication auth) {
        return projectRepo.findByNameIgnoreCase(nom).map(this::toProjetDTO);
    }

    private Project findProjetOwnedByChef(Long projectId, Long chefId) {
        Project p = projectRepo.findById(projectId).orElseThrow(() -> new NoSuchElementException("Projet introuvable"));
        if (!p.getCreatedBy().equals(chefId)) throw new SecurityException("Ce projet ne vous appartient pas");
        return p;
    }

    /**
     * Ajoute un membre actif à un projet avec le rôle spécifié.
     * <p>
     * Applique la règle métier : un employé avec le rôle {@code MEMBRE} ne peut être
     * affecté qu'à un seul projet actif à la fois. Les employés avec le rôle {@code CHEF}
     * peuvent gérer plusieurs projets simultanément.
     * </p>
     *
     * @param project    le projet auquel ajouter le membre
     * @param employeeId identifiant Oracle de l'employé à ajouter
     * @param role       rôle de l'employé dans le projet ({@code CHEF} ou {@code MEMBRE})
     * @throws IllegalArgumentException si l'employé est déjà actif sur un autre projet en tant que MEMBRE
     */
    @Transactional
    public void addMembre(Project project, Long employeeId, String role) {
        // One-active-project-per-MEMBRE rule — CHEF can manage multiple projects
        if (!"CHEF".equals(role) && !"TERMINE".equals(project.getStatus())) {
            memberRepo.findActiveByEmployeeId(employeeId).stream()
                .filter(m -> !m.getProject().getProjectId().equals(project.getProjectId()))
                .findFirst()
                .ifPresent(m -> {
                    throw new IllegalArgumentException(
                        "L'employé #" + employeeId + " est déjà affecté au projet \"" +
                        m.getProject().getName() + "\"");
                });
        }
        memberRepo.save(ProjectMember.builder()
            .project(project).employeeId(employeeId).role(role).isActive(1).build());
    }

    private void updateProjetProgression(Long projectId) {
        long total = taskRepo.countByProjectId(projectId), termines = taskRepo.countTerminesByProjectId(projectId);
        projectRepo.findById(projectId).ifPresent(p -> { p.setProgressPct(total > 0 ? (int)((termines * 100) / total) : 0); projectRepo.save(p); });
    }

    private ProjetDTO toProjetDTO(Project p) {
        List<ProjectMember> rawMembres = memberRepo.findActiveByProjectId(p.getProjectId());
        List<Long> ids = rawMembres.stream().map(ProjectMember::getEmployeeId).collect(Collectors.toList());
        Map<Long, EmployeDTO> empMap = fetchEmployeMap(ids);
        List<MembreDTO> membres = rawMembres.stream().map(m -> {
            EmployeDTO emp = empMap.getOrDefault(m.getEmployeeId(),
                EmployeDTO.builder().id(m.getEmployeeId())
                    .nomComplet("Employé #" + m.getEmployeeId()).build());
            return MembreDTO.builder()
                .id(m.getEmployeeId()).prenom(emp.getPrenom()).nom(emp.getNom())
                .nomComplet(emp.getNomComplet()).initiales(buildInitiales(emp))
                .role(m.getRole()).poste(emp.getPoste() != null ? emp.getPoste() : "")
                .email(emp.getEmail() != null ? emp.getEmail() : "")
                .telephone(emp.getTelephone()).statut(emp.getStatut() != null ? emp.getStatut() : "ACTIF")
                .dateEmbauche(emp.getDateEmbauche()).departement(emp.getDepartement())
                .photo(emp.getPhoto())
                .build();
        }).collect(Collectors.toList());
        String chefNom = membres.stream().filter(m -> "CHEF".equals(m.getRole())).map(MembreDTO::getNomComplet).findFirst().orElse("—");
        List<TacheDTO> taches = taskRepo.findByProject_ProjectIdOrderByCreatedAtAsc(p.getProjectId()).stream().map(this::toTacheDTO).collect(Collectors.toList());
        int total = taches.size();
        int terminees = (int) taches.stream().filter(TacheDTO::isTerminee).count();
        int progression = total > 0 ? (terminees * 100) / total : (p.getProgressPct() != null ? p.getProgressPct() : 0);
        return ProjetDTO.builder().id(p.getProjectId()).nom(p.getName()).description(p.getDescription()).code(p.getCode())
                .createdBy(p.getCreatedBy()).chefProjet(chefNom).deptId(p.getDeptId()).startDate(p.getStartDate()).endDate(p.getEndDate())
                .statut(p.getStatus()).priority(p.getPriority()).progression(progression)
                .totalTaches(total).tachesCompletees(terminees)
                .membres(membres).taches(taches)
                .createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt()).build();
    }

    private TacheDTO toTacheDTO(Task t) {
        return TacheDTO.builder().id(t.getTaskId()).titre(t.getTitle())
                .projetNom(t.getProject() != null ? t.getProject().getName() : "")
                .projetCouleur("#3B82F6").projetId(t.getProject() != null ? t.getProject().getProjectId() : null)
                .assignedTo(t.getAssignedTo()).priorite(t.getPriority()).echeance(t.getDueDate())
                .terminee("TERMINE".equals(t.getStatus())).statut(t.getStatus()).progressPct(t.getProgressPct())
                .description(t.getDescription()).estimatedHours(t.getEstimatedHours()).actualHours(t.getActualHours()).build();
    }

    private PerformanceEvalDTO toEvalDTO(PerformanceEval e) {
        return PerformanceEvalDTO.builder().evalId(e.getEvalId()).employeeId(e.getEmployeeId()).evaluatorId(e.getEvaluatorId())
                .periodYear(e.getPeriodYear()).periodQuarter(e.getPeriodQuarter()).score(e.getScore())
                .strengths(e.getStrengths()).improvements(e.getImprovements()).comments(e.getComments()).status(e.getStatus()).createdAt(e.getCreatedAt()).build();
    }

    private String buildInitiales(EmployeDTO emp) {
        if (emp == null) return "?";
        String p = (emp.getPrenom() != null && !emp.getPrenom().isEmpty()) ? String.valueOf(emp.getPrenom().charAt(0)).toUpperCase() : "";
        String n = (emp.getNom()    != null && !emp.getNom().isEmpty())    ? String.valueOf(emp.getNom().charAt(0)).toUpperCase()    : "";
        if (!p.isEmpty() || !n.isEmpty()) return p + n;
        // Fall back to first two initials from nomComplet
        if (emp.getNomComplet() != null && !emp.getNomComplet().isBlank()) {
            String[] parts = emp.getNomComplet().trim().split("\\s+");
            String i1 = parts.length > 0 && !parts[0].isEmpty() ? String.valueOf(parts[0].charAt(0)).toUpperCase() : "";
            String i2 = parts.length > 1 && !parts[1].isEmpty() ? String.valueOf(parts[1].charAt(0)).toUpperCase() : "";
            if (!i1.isEmpty()) return i1 + i2;
        }
        return "?";
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}