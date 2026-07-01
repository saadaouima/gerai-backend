package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data JPA pour la gestion des commentaires de tâches.
 * <p>
 * Fournit les opérations CRUD standard sur la table {@code TASK_COMMENTS}
 * ainsi qu'une requête de consultation chronologique des commentaires d'une tâche.
 * </p>
 *
 * @since 1.0
 */
@Repository
public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {

    /**
     * Retourne les commentaires d'une tâche triés chronologiquement (du plus ancien au plus récent).
     *
     * @param taskId identifiant de la tâche
     * @return liste des commentaires dans l'ordre de création ascendant
     */
    List<TaskComment> findByTask_TaskIdOrderByCreatedAtAsc(Long taskId);
}