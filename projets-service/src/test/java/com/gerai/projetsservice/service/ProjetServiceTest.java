package com.gerai.projetsservice.service;

import com.gerai.projetsservice.config.JwtHelperInterface;
import com.gerai.projetsservice.dto.TacheDTO;
import com.gerai.projetsservice.model.Project;
import com.gerai.projetsservice.model.Task;
import com.gerai.projetsservice.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjetServiceTest {

    // ── Mocked dependencies (match ProjetService constructor order) ───────────
    @Mock private ProjectRepository         projectRepo;
    @Mock private ProjectMemberRepository   memberRepo;
    @Mock private TaskRepository            taskRepo;
    @Mock private TaskCommentRepository     commentRepo;
    @Mock private PerformanceEvalRepository evalRepo;
    @Mock private JwtHelperInterface         jwt;
    @Mock private EmployeService            employeService;
    @Mock private ProjectNotificationService notifService;
    @Mock private JdbcTemplate              jdbc;

    @InjectMocks
    private ProjetService service;

    // ── Shared authentication mock ────────────────────────────────────────────
    @Mock
    private Authentication auth;

    // ── Test fixtures ─────────────────────────────────────────────────────────
    private static final Long EMP_ID     = 10L;
    private static final Long PROJECT_ID = 100L;
    private static final Long TASK_ID    = 1L;

    private Project project;
    private Task    task;

    @BeforeEach
    void setUp() {
        project = Project.builder()
            .projectId(PROJECT_ID)
            .name("Projet Test")
            .createdBy(EMP_ID)
            .status("EN_COURS")
            .progressPct(0)
            .build();

        task = Task.builder()
            .taskId(TASK_ID)
            .title("Tâche Test")
            .assignedTo(EMP_ID)
            .status("EN_COURS")
            .progressPct(50)
            .project(project)
            .build();
    }

    // ─── Test 1 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("1 — toggleTache : EN_COURS → TERMINE, progression recalculée à 60 % (3/5)")
    void whenToggleTacheToTermine_thenProgressionIs60Percent() {
        when(jwt.getEmployeeId(auth)).thenReturn(EMP_ID);
        when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(taskRepo.countByProjectId(PROJECT_ID)).thenReturn(5L);
        when(taskRepo.countTerminesByProjectId(PROJECT_ID)).thenReturn(3L);
        when(projectRepo.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(taskRepo.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TacheDTO result = service.toggleTache(TASK_ID, auth);

        // Task transitions to TERMINE
        assertThat(result.getStatut()).isEqualTo("TERMINE");
        assertThat(result.isTerminee()).isTrue();

        // Project progression recalculated: (3 * 100) / 5 = 60
        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepo).save(projectCaptor.capture());
        assertThat(projectCaptor.getValue().getProgressPct()).isEqualTo(60);
    }

    // ─── Test 2 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("2 — toggleTache : seul l'assigné peut modifier → SecurityException pour un autre employé")
    void whenNonAssigneeTogglesTache_thenSecurityException() {
        Long otherEmpId = 99L; // different from task.assignedTo = 10L
        when(jwt.getEmployeeId(auth)).thenReturn(otherEmpId);
        when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.toggleTache(TASK_ID, auth))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Seul l'assigné peut modifier");

        verify(taskRepo, never()).save(any());
        verify(projectRepo, never()).save(any());
    }

    // ─── Test 3 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("3 — toggleTache : 0 tâches dans le projet → progression = 0 sans ArithmeticException")
    void whenProjectHasNoTasks_thenProgressionIsZero() {
        when(jwt.getEmployeeId(auth)).thenReturn(EMP_ID);
        when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(taskRepo.countByProjectId(PROJECT_ID)).thenReturn(0L);      // total = 0
        when(taskRepo.countTerminesByProjectId(PROJECT_ID)).thenReturn(0L);
        when(projectRepo.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(taskRepo.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        // Must not throw ArithmeticException (division by zero guard)
        assertThatCode(() -> service.toggleTache(TASK_ID, auth))
            .doesNotThrowAnyException();

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepo).save(captor.capture());
        assertThat(captor.getValue().getProgressPct()).isEqualTo(0);
    }

    // ─── Test 4 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("4 — updateAvancement(100) : tâche → TERMINE, progression projet recalculée")
    void whenAvancementSetTo100_thenTaskIsTermineAndProjectProgressionUpdated() {
        Task freshTask = Task.builder()
            .taskId(TASK_ID).title("Tâche").assignedTo(EMP_ID)
            .status("A_FAIRE").progressPct(0).project(project).build();

        when(jwt.getEmployeeId(auth)).thenReturn(EMP_ID);
        when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(freshTask));
        when(taskRepo.countByProjectId(PROJECT_ID)).thenReturn(5L);
        when(taskRepo.countTerminesByProjectId(PROJECT_ID)).thenReturn(3L);
        when(projectRepo.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(taskRepo.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TacheDTO result = service.updateAvancement(TASK_ID, 100, auth);

        assertThat(result.getStatut()).isEqualTo("TERMINE");
        assertThat(result.getProgressPct()).isEqualTo(100);

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepo).save(captor.capture());
        assertThat(captor.getValue().getProgressPct()).isEqualTo(60); // (3*100)/5
    }

    // ─── Test 5 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("5 — updateAvancement(50) : A_FAIRE → EN_COURS, progressPct = 50")
    void whenAvancementSetTo50FromAFaire_thenStatusIsEnCours() {
        Task freshTask = Task.builder()
            .taskId(TASK_ID).title("Tâche").assignedTo(EMP_ID)
            .status("A_FAIRE").progressPct(0).project(project).build();

        when(jwt.getEmployeeId(auth)).thenReturn(EMP_ID);
        when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(freshTask));
        when(taskRepo.countByProjectId(PROJECT_ID)).thenReturn(5L);
        when(taskRepo.countTerminesByProjectId(PROJECT_ID)).thenReturn(2L);
        when(projectRepo.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(taskRepo.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TacheDTO result = service.updateAvancement(TASK_ID, 50, auth);

        assertThat(result.getStatut()).isEqualTo("EN_COURS");
        assertThat(result.getProgressPct()).isEqualTo(50);
        assertThat(result.isTerminee()).isFalse();

        // notifierTacheTerminee must NOT be called since task did not reach TERMINE
        verify(notifService, never()).notifierTacheTerminee(any(), any());
    }

    // ─── Test 6 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("6 — getProjetById : employé non membre et non admin → SecurityException")
    void whenUnauthorizedEmployeeAccessesProjet_thenSecurityException() {
        Long unauthorizedEmpId = 99L;
        Project otherProject = Project.builder()
            .projectId(PROJECT_ID)
            .name("Projet Privé")
            .createdBy(20L) // owned by someone else
            .build();

        when(jwt.getEmployeeId(auth)).thenReturn(unauthorizedEmpId);
        when(projectRepo.findById(PROJECT_ID)).thenReturn(Optional.of(otherProject));
        when(jwt.isAdminOrRh(auth)).thenReturn(false);
        when(memberRepo.findByProject_ProjectIdAndEmployeeId(PROJECT_ID, unauthorizedEmpId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProjetById(PROJECT_ID, auth))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Accès interdit");
    }

    // ─── Test 7 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("7 — deleteProjet : chef ne possédant pas le projet → SecurityException")
    void whenChefDeletesProjectHeDoesNotOwn_thenSecurityException() {
        Project ownedByOther = Project.builder()
            .projectId(PROJECT_ID)
            .name("Projet d'un autre chef")
            .createdBy(20L) // different from EMP_ID = 10L
            .build();

        when(jwt.getEmployeeId(auth)).thenReturn(EMP_ID);
        when(projectRepo.findById(PROJECT_ID)).thenReturn(Optional.of(ownedByOther));

        assertThatThrownBy(() -> service.deleteProjet(PROJECT_ID, auth))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Ce projet ne vous appartient pas");

        verify(projectRepo, never()).delete(any());
    }
}
