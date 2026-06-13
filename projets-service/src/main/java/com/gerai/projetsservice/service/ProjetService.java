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

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjetService {

    private final ProjectRepository         projectRepo;
    private final ProjectMemberRepository   memberRepo;
    private final TaskRepository            taskRepo;
    private final TaskCommentRepository     commentRepo;
    private final PerformanceEvalRepository evalRepo;
    private final JwtHelperInterface         jwt;
    private final EmployeService            employeService;
    private final ProjectNotificationService notifService;
    private final JdbcTemplate              jdbc;

    @Transactional(readOnly = true)
    public List<ProjetDTO> getProjetsChef(Authentication auth) {
        Long chefId = jwt.getEmployeeId(auth);
        return projectRepo.findByCreatedByOrderByCreatedAtDesc(chefId)
                .stream().map(this::toProjetDTO).collect(Collectors.toList());
    }

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

    @Transactional
    public void deleteProjet(Long projectId, Authentication auth) {
        Long chefId = jwt.getEmployeeId(auth);
        projectRepo.delete(findProjetOwnedByChef(projectId, chefId));
    }

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

    @Transactional(readOnly = true)
    public List<ProjetDTO> getMesProjets(Authentication auth) {
        return projectRepo.findByMemberEmployeeId(jwt.getEmployeeId(auth))
                .stream().map(this::toProjetDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProjetDTO getProjetById(Long projectId, Authentication auth) {
        Long empId = jwt.getEmployeeId(auth);
        Project project = projectRepo.findById(projectId).orElseThrow(() -> new NoSuchElementException("Projet introuvable"));
        boolean hasAccess = jwt.isAdminOrRh(auth) || project.getCreatedBy().equals(empId)
                || memberRepo.findByProject_ProjectIdAndEmployeeId(projectId, empId).isPresent();
        if (!hasAccess) throw new SecurityException("Accès interdit à ce projet");
        return toProjetDTO(project);
    }

    @Transactional(readOnly = true)
    public List<TacheDTO> getMesTaches(Authentication auth) {
        return taskRepo.findMesTaches(jwt.getEmployeeId(auth)).stream().map(this::toTacheDTO).collect(Collectors.toList());
    }

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

    @Transactional
    public void deleteTache(Long taskId, Authentication auth) {
        if (!taskRepo.existsById(taskId)) {
            throw new IllegalArgumentException("Tâche introuvable: " + taskId);
        }
        taskRepo.deleteById(taskId);
        log.info("[Tache] Supprimée id={}", taskId);
    }

    @Transactional(readOnly = true)
    public List<TacheDTO> getTachesChef(Authentication auth) {
        Long chefId = jwt.getEmployeeId(auth);
        return projectRepo.findByCreatedByOrderByCreatedAtDesc(chefId).stream()
                .flatMap(p -> taskRepo.findByProject_ProjectIdOrderByCreatedAtAsc(p.getProjectId()).stream())
                .map(this::toTacheDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProjetDTO> getAllProjets() {
        return projectRepo.findAll().stream().map(this::toProjetDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DashboardDTO getDashboard() {
        long total = projectRepo.countAll(), taches = taskRepo.countAll(), tachesOk = taskRepo.countAllTerminees();
        return DashboardDTO.builder()
                .totalProjets(total).projetsEnCours(projectRepo.countByStatus("EN_COURS"))
                .projetsTermines(projectRepo.countByStatus("TERMINE")).projetsEnAttente(projectRepo.countByStatus("PLANIFIE"))
                .projetsEnPause(projectRepo.countByStatus("EN_PAUSE")).totalTaches(taches).tachesTerminees(tachesOk)
                .tauxCompletion(taches > 0 ? round2((tachesOk * 100.0) / taches) : 0).build();
    }

    @Transactional
    public PerformanceEvalDTO createEval(PerformanceEvalDTO req, Authentication auth) {
        PerformanceEval eval = PerformanceEval.builder().employeeId(req.getEmployeeId()).evaluatorId(jwt.getEmployeeId(auth))
                .periodYear(req.getPeriodYear()).periodQuarter(req.getPeriodQuarter()).score(req.getScore())
                .strengths(req.getStrengths()).improvements(req.getImprovements()).comments(req.getComments()).status("SOUMIS").build();
        return toEvalDTO(evalRepo.save(eval));
    }

    @Transactional(readOnly = true)
    public List<PerformanceEvalDTO> getEvals() { return evalRepo.findAll().stream().map(this::toEvalDTO).collect(Collectors.toList()); }

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
        String inClause = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT e.EMPLOYEE_ID, e.FIRST_NAME, e.LAST_NAME, e.EMAIL, e.PHONE, " +
                     "       e.PHOTO_URL, e.STATUS, TO_CHAR(e.HIRE_DATE, 'YYYY-MM-DD') AS HIRE_DATE_STR, " +
                     "       d.NOM AS DEPT_NOM " +
                     "FROM GERAI.EMPLOYEES e " +
                     "LEFT JOIN GERAI.ADMIN_DEPARTEMENTS d ON e.DEPT_ID = d.DEPT_ADMIN_ID " +
                     "WHERE e.EMPLOYEE_ID IN (" + inClause + ")";
        try {
            return jdbc.query(sql, ids.toArray(), (rs, i) -> EmployeDTO.builder()
                .id(rs.getLong("EMPLOYEE_ID"))
                .prenom(rs.getString("FIRST_NAME"))
                .nom(rs.getString("LAST_NAME"))
                .nomComplet(rs.getString("FIRST_NAME") + " " + rs.getString("LAST_NAME"))
                .email(rs.getString("EMAIL"))
                .telephone(rs.getString("PHONE"))
                .photo(rs.getString("PHOTO_URL"))
                .statut(rs.getString("STATUS"))
                .dateEmbauche(rs.getString("HIRE_DATE_STR"))
                .departement(rs.getString("DEPT_NOM"))
                .poste("")
                .build()
            ).stream().collect(Collectors.toMap(EmployeDTO::getId, e -> e));
        } catch (Exception e) {
            log.warn("[ProjetService] fetchEmployeMap failed: {}", e.getMessage());
            return Map.of();
        }
    }

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

    /** Consommé par taches-service via Feign (GET /api/projets/by-name) */
    @Transactional(readOnly = true)
    public Optional<ProjetDTO> findByName(String nom, Authentication auth) {
        return projectRepo.findByNameIgnoreCase(nom).map(this::toProjetDTO);
    }

    private Project findProjetOwnedByChef(Long projectId, Long chefId) {
        Project p = projectRepo.findById(projectId).orElseThrow(() -> new NoSuchElementException("Projet introuvable"));
        if (!p.getCreatedBy().equals(chefId)) throw new SecurityException("Ce projet ne vous appartient pas");
        return p;
    }

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
                EmployeDTO.builder().id(m.getEmployeeId()).prenom("Emp").nom("#" + m.getEmployeeId())
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
        return ProjetDTO.builder().id(p.getProjectId()).nom(p.getName()).description(p.getDescription()).code(p.getCode())
                .createdBy(p.getCreatedBy()).chefProjet(chefNom).deptId(p.getDeptId()).startDate(p.getStartDate()).endDate(p.getEndDate())
                .statut(p.getStatus()).priority(p.getPriority()).progression(p.getProgressPct()).membres(membres).taches(taches)
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
        if (emp == null) return "??";
        String p = (emp.getPrenom() != null && !emp.getPrenom().isEmpty()) ? String.valueOf(emp.getPrenom().charAt(0)).toUpperCase() : "";
        String n = (emp.getNom() != null && !emp.getNom().isEmpty()) ? String.valueOf(emp.getNom().charAt(0)).toUpperCase() : "";
        return p + n;
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}