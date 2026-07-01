package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository Spring Data JPA pour la gestion des membres de projets.
 * <p>
 * Fournit les opérations CRUD standard sur la table {@code PROJECT_MEMBERS}
 * ainsi que des requêtes JPQL spécifiques pour le filtrage par projet ou par employé.
 * </p>
 *
 * @since 1.0
 */
@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    /**
     * Retourne tous les membres (actifs et inactifs) d'un projet donné.
     *
     * @param projectId identifiant du projet
     * @return liste de toutes les appartenances au projet
     */
    List<ProjectMember> findByProject_ProjectId(Long projectId);

    /**
     * Recherche l'appartenance d'un employé spécifique à un projet donné.
     *
     * @param projectId  identifiant du projet
     * @param employeeId identifiant Oracle de l'employé
     * @return l'appartenance si elle existe
     */
    Optional<ProjectMember> findByProject_ProjectIdAndEmployeeId(Long projectId, Long employeeId);

    /**
     * Retourne les membres actifs d'un projet (flag {@code IS_ACTIVE = 1}).
     *
     * @param projectId identifiant du projet
     * @return liste des membres actifs du projet
     */
    @Query("""
            SELECT pm FROM ProjectMember pm
            WHERE pm.project.projectId = :projectId
              AND pm.isActive = 1
            """)
    List<ProjectMember> findActiveByProjectId(@Param("projectId") Long projectId);

    /**
     * Supprime l'appartenance d'un employé à un projet.
     *
     * @param projectId  identifiant du projet
     * @param employeeId identifiant Oracle de l'employé à retirer
     */
    void deleteByProject_ProjectIdAndEmployeeId(Long projectId, Long employeeId);

    /**
     * Retourne les projets actifs auxquels un employé participe, avec chargement du projet en jointure.
     *
     * @param employeeId identifiant Oracle de l'employé
     * @return liste des appartenances actives de l'employé avec le projet pré-chargé
     */
    @Query("""
            SELECT pm FROM ProjectMember pm
            JOIN FETCH pm.project
            WHERE pm.employeeId = :employeeId
              AND pm.isActive = 1
            """)
    List<ProjectMember> findActiveByEmployeeId(@Param("employeeId") Long employeeId);
}