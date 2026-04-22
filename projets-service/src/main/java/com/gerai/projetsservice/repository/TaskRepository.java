package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    /** Toutes les tâches d'un projet */
    List<Task> findByProject_ProjectIdOrderByCreatedAtAsc(Long projectId);

    /** Tâches assignées à un employé (espace Employé — mes-taches) */
    @Query("""
            SELECT t FROM Task t
            WHERE t.assignedTo = :employeeId
              AND t.status <> 'TERMINE'
            ORDER BY t.dueDate ASC NULLS LAST, t.createdAt DESC
            """)
    List<Task> findMesTaches(@Param("employeeId") Long employeeId);

    /** Toutes les tâches d'un employé (y compris terminées) */
    List<Task> findByAssignedToOrderByCreatedAtDesc(Long employeeId);

    /** Tâches d'un projet filtrées par statut */
    List<Task> findByProject_ProjectIdAndStatus(Long projectId, String status);

    /** Comptage pour progression automatique */
    @Query("SELECT COUNT(t) FROM Task t WHERE t.project.projectId = :projectId")
    long countByProjectId(@Param("projectId") Long projectId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.project.projectId = :projectId AND t.status = 'TERMINE'")
    long countTerminesByProjectId(@Param("projectId") Long projectId);

    @Query("SELECT COUNT(t) FROM Task t")
    long countAll();

    @Query("SELECT COUNT(t) FROM Task t WHERE t.status = 'TERMINE'")
    long countAllTerminees();
}