package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data JPA pour la gestion des tâches de projets.
 * <p>
 * Fournit les opérations CRUD standard sur la table {@code TASKS}
 * ainsi que des requêtes JPQL spécifiques pour le filtrage par projet,
 * par employé assigné et par statut, ainsi que des comptages pour
 * le calcul automatique de la progression des projets.
 * </p>
 *
 * @since 1.0
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * Retourne toutes les tâches d'un projet, triées par date de création ascendante.
     *
     * @param projectId identifiant du projet
     * @return liste des tâches du projet dans l'ordre de création
     */
    List<Task> findByProject_ProjectIdOrderByCreatedAtAsc(Long projectId);

    /**
     * Retourne les tâches non terminées assignées à un employé, triées par date d'échéance puis de création.
     * <p>
     * Utilisé pour l'espace Employé ({@code GET /api/projets/mes-taches}).
     * </p>
     *
     * @param employeeId identifiant Oracle de l'employé assigné
     * @return liste des tâches en cours de l'employé, par ordre d'échéance ascendant
     */
    @Query("""
            SELECT t FROM Task t
            WHERE t.assignedTo = :employeeId
              AND t.status <> 'TERMINE'
            ORDER BY t.dueDate ASC NULLS LAST, t.createdAt DESC
            """)
    List<Task> findMesTaches(@Param("employeeId") Long employeeId);

    /**
     * Retourne toutes les tâches assignées à un employé (y compris les terminées).
     *
     * @param employeeId identifiant Oracle de l'employé assigné
     * @return liste de toutes les tâches de l'employé par date de création décroissante
     */
    List<Task> findByAssignedToOrderByCreatedAtDesc(Long employeeId);

    /**
     * Retourne les tâches d'un projet filtrées par statut.
     *
     * @param projectId identifiant du projet
     * @param status    statut recherché (ex. {@code A_FAIRE}, {@code EN_COURS}, {@code TERMINEE})
     * @return liste des tâches du projet ayant le statut donné
     */
    List<Task> findByProject_ProjectIdAndStatus(Long projectId, String status);

    /**
     * Compte le nombre total de tâches d'un projet (utilisé pour la progression automatique).
     *
     * @param projectId identifiant du projet
     * @return nombre total de tâches du projet
     */
    @Query("SELECT COUNT(t) FROM Task t WHERE t.project.projectId = :projectId")
    long countByProjectId(@Param("projectId") Long projectId);

    /**
     * Compte le nombre de tâches terminées d'un projet.
     *
     * @param projectId identifiant du projet
     * @return nombre de tâches au statut {@code TERMINE} dans le projet
     */
    @Query("SELECT COUNT(t) FROM Task t WHERE t.project.projectId = :projectId AND t.status = 'TERMINE'")
    long countTerminesByProjectId(@Param("projectId") Long projectId);

    /**
     * Compte le nombre total de tâches dans la plateforme.
     *
     * @return nombre total de tâches (tous projets confondus)
     */
    @Query("SELECT COUNT(t) FROM Task t")
    long countAll();

    /**
     * Compte le nombre total de tâches terminées dans la plateforme.
     *
     * @return nombre total de tâches au statut {@code TERMINE}
     */
    @Query("SELECT COUNT(t) FROM Task t WHERE t.status = 'TERMINE'")
    long countAllTerminees();
}