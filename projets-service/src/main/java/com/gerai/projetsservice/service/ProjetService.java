package com.gerai.projetsservice.service;

import com.gerai.projetsservice.config.JwtHelper;
import com.gerai.projetsservice.dto.*;
import com.gerai.projetsservice.model.*;
import com.gerai.projetsservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final JwtHelper                 jwt;
    private final EmployeService            employeService;
    private final ProjectNotificationService notifService;

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
        notifService.notifierProjetCree(saved, chefId);
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
        if (req.getTitre()         != null) task.setTitle(req.getTitre());
        if (req.getDescription()   != null) task.setDescription(req.getDescription());
        if (req.getAssignedTo()    != null) task.setAssignedTo(req.getAssignedTo());
        if (req.getEcheance()      != null) task.setDueDate(req.getEcheance());
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

    public List<EmployeDTO> getEmployes() { return employeService.getAllEmployes(); }

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

    public void addMembre(Project project, Long employeeId, String role) {
        memberRepo.save(ProjectMember.builder().project(project).employeeId(employeeId).role(role).isActive(1).build());
    }

    private void updateProjetProgression(Long projectId) {
        long total = taskRepo.countByProjectId(projectId), termines = taskRepo.countTerminesByProjectId(projectId);
        projectRepo.findById(projectId).ifPresent(p -> { p.setProgressPct(total > 0 ? (int)((termines * 100) / total) : 0); projectRepo.save(p); });
    }

    private ProjetDTO toProjetDTO(Project p) {
        List<MembreDTO> membres = memberRepo.findActiveByProjectId(p.getProjectId()).stream().map(m -> {
            EmployeDTO emp = employeService.getEmployeById(m.getEmployeeId());
            return MembreDTO.builder().id(m.getEmployeeId()).prenom(emp != null ? emp.getPrenom() : "").nom(emp != null ? emp.getNom() : "")
                    .nomComplet(emp != null ? emp.getNomComplet() : "").initiales(buildInitiales(emp)).role(m.getRole()).poste(emp != null ? emp.getPoste() : "").build();
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