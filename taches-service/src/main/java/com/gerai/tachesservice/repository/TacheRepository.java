package com.gerai.tachesservice.repository;

import com.gerai.tachesservice.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository sur GERAI.TASKS.
 *
 * Colonnes TASKS (V2__projects_tasks.sql) :
 *   task_id, project_id, title, description, assigned_to,
 *   created_by, parent_task_id, priority, status,
 *   progress_pct, due_date, estimated_hours, actual_hours,
 *   created_at, updated_at
 *
 * Statuts CHECK : A_FAIRE | EN_COURS | EN_REVUE | TERMINE | BLOQUE
 * Priorités CHECK : FAIBLE | NORMALE | HAUTE | CRITIQUE
 */
@Repository
public interface TacheRepository extends JpaRepository<Task, Long> {

    /* ── Espace Chef : toutes les tâches d'un projet ────── */

    List<Task> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<Task> findByProjectIdAndStatusOrderByCreatedAtDesc(Long projectId, String status);

    /* ── Espace Employé : ses propres tâches ─────────────── */

    List<Task> findByAssignedToOrderByCreatedAtDesc(Long assignedTo);

    List<Task> findByAssignedToAndStatusNotOrderByCreatedAtDesc(Long assignedTo, String status);

    /* ── Tâches actives d'un employé (non terminées) ──────
       Endpoint: GET /api/taches/actives
       Utilisé par TacheService.getTachesActives() côté Angular
    */
    @Query(value = """
            SELECT t.* FROM GERAI.TASKS t
            WHERE t.ASSIGNED_TO = :employeeId
              AND t.STATUS NOT IN ('TERMINE','BLOQUE')
            ORDER BY t.DUE_DATE ASC NULLS LAST
            """, nativeQuery = true)
    List<Task> findActivesForEmployee(@Param("employeeId") Long employeeId);

    /* ── Tâches des employés d'un département (Chef) ──────
       Pour le dashboard Chef : voir toutes les tâches de son équipe
    */
    @Query(value = """
            SELECT t.* FROM GERAI.TASKS t
            JOIN GERAI.EMPLOYEES e ON t.ASSIGNED_TO = e.EMPLOYEE_ID
            WHERE e.DEPT_ID = :deptId
              AND t.STATUS <> 'TERMINE'
            ORDER BY t.DUE_DATE ASC NULLS LAST
            """, nativeQuery = true)
    List<Task> findOuvertesParDept(@Param("deptId") Long deptId);

    /* ── Tâches des projets d'un département (Chef) ───────
       Projets créés par les employés du département
    */
    @Query(value = """
            SELECT t.* FROM GERAI.TASKS t
            JOIN GERAI.PROJECTS p ON t.PROJECT_ID = p.PROJECT_ID
            JOIN GERAI.EMPLOYEES e ON p.CREATED_BY = e.EMPLOYEE_ID
            WHERE e.DEPT_ID = :deptId
            ORDER BY t.CREATED_AT DESC
            """, nativeQuery = true)
    List<Task> findByDeptId(@Param("deptId") Long deptId);

    /* ── Recherche par nom complet de l'assigné (fallback) ─
       Angular envoie "Prénom Nom" dans le champ assigneA
    */
    @Query(value = """
            SELECT t.* FROM GERAI.TASKS t
            JOIN GERAI.EMPLOYEES e ON t.ASSIGNED_TO = e.EMPLOYEE_ID
            WHERE UPPER(e.FIRST_NAME || ' ' || e.LAST_NAME) = UPPER(:fullName)
            ORDER BY t.CREATED_AT DESC
            """, nativeQuery = true)
    List<Task> findByAssigneeFullName(@Param("fullName") String fullName);

    /** Tâches en retard : échéance dépassée, statut actif (ni terminé ni bloqué). */
    @Query(value = """
            SELECT t.* FROM GERAI.TASKS t
            WHERE t.STATUS NOT IN ('TERMINE', 'BLOQUE')
              AND t.DUE_DATE < :today
              AND t.CREATED_BY IS NOT NULL
            ORDER BY t.DUE_DATE ASC
            """, nativeQuery = true)
    List<Task> findOverdueTasks(@Param("today") LocalDate today);
}