package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository Spring Data JPA pour la gestion des projets.
 * <p>
 * Fournit les opérations CRUD standard sur la table {@code PROJECTS}
 * ainsi que des requêtes JPQL spécifiques pour le filtrage par chef,
 * membre, département et statut.
 * </p>
 *
 * @since 1.0
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * Retourne les projets créés par un chef donné, triés par date de création décroissante.
     *
     * @param createdBy identifiant Oracle du chef de projet
     * @return liste des projets du chef
     */
    List<Project> findByCreatedByOrderByCreatedAtDesc(Long createdBy);

    /**
     * Retourne les projets auxquels un employé participe activement (membre actif).
     * <p>
     * Utilisé pour l'espace Employé ({@code GET /api/projets}) pour afficher les projets
     * de l'utilisateur authentifié.
     * </p>
     *
     * @param employeeId identifiant Oracle de l'employé membre
     * @return liste des projets actifs de l'employé, triée par date de création décroissante
     */
    @Query("""
            SELECT p FROM Project p
            WHERE EXISTS (
                SELECT 1 FROM ProjectMember m
                WHERE m.project = p
                  AND m.employeeId = :employeeId
                  AND m.isActive = 1
            )
            ORDER BY p.createdAt DESC
            """)
    List<Project> findByMemberEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * Retourne les projets appartenant à un département donné, triés par date de création décroissante.
     *
     * @param deptId identifiant du département
     * @return liste des projets du département
     */
    List<Project> findByDeptIdOrderByCreatedAtDesc(Long deptId);

    /**
     * Compte le nombre de projets ayant un statut donné.
     *
     * @param status statut recherché (ex. {@code EN_COURS}, {@code TERMINE})
     * @return nombre de projets correspondant au statut
     */
    @Query("SELECT COUNT(p) FROM Project p WHERE p.status = :status")
    long countByStatus(@Param("status") String status);

    /**
     * Compte le nombre total de projets dans la plateforme.
     *
     * @return nombre total de projets
     */
    @Query("SELECT COUNT(p) FROM Project p")
    long countAll();

    /**
     * Recherche un projet par son nom exact (insensible à la casse).
     * <p>
     * Appelé depuis {@code ProjetService.findByName()} qui est consommé
     * par {@code taches-service} via Feign ({@code ProjetClient.findByName()}).
     * </p>
     *
     * @param name nom exact du projet à rechercher
     * @return le projet correspondant si trouvé
     */
    Optional<Project> findByNameIgnoreCase(String name);
}