package com.gerai.tachesservice.repository;

import com.gerai.tachesservice.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository Spring Data JPA pour les opérations CRUD et les requêtes métier sur GERAI.TASKS.
 * <p>
 * {@code @Repository} : déclare ce bean comme composant de persistance Spring,
 * active la traduction des exceptions JPA en exceptions Spring.
 * <p>
 * Ce repository est le seul point d'accès à la table TASKS dans taches-service.
 * Il combine des méthodes dérivées (Spring Data naming convention) et des requêtes
 * natives SQL pour les cas complexes (jointures, filtres multi-critères).
 * <p>
 * Colonnes TASKS (V2__projects_tasks.sql) :
 * task_id, project_id, title, description, assigned_to, created_by,
 * parent_task_id, priority, status, progress_pct, due_date,
 * estimated_hours, actual_hours, created_at, updated_at.
 * <p>
 * Contraintes CHECK Oracle :
 * <ul>
 *   <li>Statuts : A_FAIRE | EN_COURS | EN_REVUE | TERMINE | BLOQUE</li>
 *   <li>Priorités : FAIBLE | NORMALE | HAUTE | CRITIQUE</li>
 * </ul>
 *
 * @since 1.0
 */
@Repository
public interface TacheRepository extends JpaRepository<Task, Long> {

    /* ── Espace Chef : toutes les tâches d'un projet ────── */

    /**
     * Récupère toutes les tâches d'un projet, triées par date de création décroissante.
     * <p>
     * Utilisé dans l'espace Chef pour afficher les tâches d'un projet sélectionné.
     *
     * @param projectId identifiant Oracle du projet (TASKS.project_id)
     * @return liste des tâches du projet, de la plus récente à la plus ancienne
     */
    List<Task> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    /**
     * Récupère les tâches d'un projet filtrées par statut, triées par date de création décroissante.
     *
     * @param projectId identifiant Oracle du projet (TASKS.project_id)
     * @param status    statut Oracle à filtrer (ex : {@code "EN_COURS"})
     * @return liste des tâches du projet correspondant au statut donné
     */
    List<Task> findByProjectIdAndStatusOrderByCreatedAtDesc(Long projectId, String status);

    /* ── Espace Employé : ses propres tâches ─────────────── */

    /**
     * Récupère toutes les tâches assignées à un employé, triées par date de création décroissante.
     * <p>
     * Utilisé par l'endpoint {@code GET /api/taches} pour l'espace Employé.
     *
     * @param assignedTo identifiant Oracle de l'employé assigné (TASKS.assigned_to)
     * @return liste complète des tâches de l'employé
     */
    List<Task> findByAssignedToOrderByCreatedAtDesc(Long assignedTo);

    /**
     * Récupère les tâches d'un employé en excluant un statut donné, triées par date de création décroissante.
     *
     * @param assignedTo identifiant Oracle de l'employé assigné (TASKS.assigned_to)
     * @param status     statut Oracle à exclure (ex : {@code "TERMINE"})
     * @return liste des tâches de l'employé dont le statut est différent de celui fourni
     */
    List<Task> findByAssignedToAndStatusNotOrderByCreatedAtDesc(Long assignedTo, String status);

    /**
     * Récupère les tâches actives (non terminées, non bloquées) d'un employé,
     * triées par date d'échéance croissante (les plus urgentes en premier).
     * <p>
     * Utilisé par l'endpoint {@code GET /api/taches/actives} ({@code TacheService.getTachesActives()}).
     * Les valeurs {@code NULL} dans {@code DUE_DATE} sont placées en fin de liste.
     *
     * @param employeeId identifiant Oracle de l'employé (TASKS.assigned_to)
     * @return liste des tâches actives de l'employé, triées par échéance
     */
    @Query(value = """
            SELECT t.* FROM GERAI.TASKS t
            WHERE t.ASSIGNED_TO = :employeeId
              AND t.STATUS NOT IN ('TERMINE','BLOQUE')
            ORDER BY t.DUE_DATE ASC NULLS LAST
            """, nativeQuery = true)
    List<Task> findActivesForEmployee(@Param("employeeId") Long employeeId);

    /**
     * Récupère les tâches ouvertes (non terminées) de tous les employés d'un département,
     * triées par date d'échéance croissante.
     * <p>
     * Utilisé pour le tableau de bord Chef afin de visualiser les tâches de toute l'équipe.
     *
     * @param deptId identifiant Oracle du département (EMPLOYEES.dept_id)
     * @return liste des tâches ouvertes des employés du département
     */
    @Query(value = """
            SELECT t.* FROM GERAI.TASKS t
            JOIN GERAI.EMPLOYEES e ON t.ASSIGNED_TO = e.EMPLOYEE_ID
            WHERE e.DEPT_ID = :deptId
              AND t.STATUS <> 'TERMINE'
            ORDER BY t.DUE_DATE ASC NULLS LAST
            """, nativeQuery = true)
    List<Task> findOuvertesParDept(@Param("deptId") Long deptId);

    /**
     * Récupère toutes les tâches appartenant aux projets créés par les employés d'un département,
     * triées par date de création décroissante.
     * <p>
     * Permet au Chef de visualiser l'ensemble des tâches des projets de son département.
     *
     * @param deptId identifiant Oracle du département (EMPLOYEES.dept_id)
     * @return liste de toutes les tâches des projets du département
     */
    @Query(value = """
            SELECT t.* FROM GERAI.TASKS t
            JOIN GERAI.PROJECTS p ON t.PROJECT_ID = p.PROJECT_ID
            JOIN GERAI.EMPLOYEES e ON p.CREATED_BY = e.EMPLOYEE_ID
            WHERE e.DEPT_ID = :deptId
            ORDER BY t.CREATED_AT DESC
            """, nativeQuery = true)
    List<Task> findByDeptId(@Param("deptId") Long deptId);

    /**
     * Recherche les tâches assignées à un employé identifié par son nom complet.
     * <p>
     * Fallback utilisé lorsque Angular envoie {@code assigneA} sous forme "Prénom Nom"
     * plutôt qu'un identifiant numérique. Comparaison insensible à la casse.
     *
     * @param fullName nom complet de l'assigné au format "Prénom Nom"
     * @return liste des tâches assignées à l'employé correspondant
     */
    @Query(value = """
            SELECT t.* FROM GERAI.TASKS t
            JOIN GERAI.EMPLOYEES e ON t.ASSIGNED_TO = e.EMPLOYEE_ID
            WHERE UPPER(e.FIRST_NAME || ' ' || e.LAST_NAME) = UPPER(:fullName)
            ORDER BY t.CREATED_AT DESC
            """, nativeQuery = true)
    List<Task> findByAssigneeFullName(@Param("fullName") String fullName);

    /**
     * Récupère toutes les tâches dont la date d'échéance est dépassée et dont le statut
     * est encore actif (ni terminé ni bloqué).
     * <p>
     * Utilisé exclusivement par {@code TacheOverdueScheduler} pour détecter
     * les tâches en retard et envoyer des notifications consolidées aux chefs.
     *
     * @param today date du jour servant de référence pour la comparaison
     * @return liste des tâches en retard, triées par échéance croissante
     */
    @Query(value = """
            SELECT t.* FROM GERAI.TASKS t
            WHERE t.STATUS NOT IN ('TERMINE', 'BLOQUE')
              AND t.DUE_DATE < :today
              AND t.CREATED_BY IS NOT NULL
            ORDER BY t.DUE_DATE ASC
            """, nativeQuery = true)
    List<Task> findOverdueTasks(@Param("today") LocalDate today);
}