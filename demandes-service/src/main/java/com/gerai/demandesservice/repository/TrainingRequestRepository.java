package com.gerai.demandesservice.repository;

import com.gerai.demandesservice.model.TrainingRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository Spring Data JPA pour les demandes de formation professionnelle
 * (table {@code GERAI.TRAINING_REQUESTS}).
 * <p>
 * {@code @Repository} : marque cette interface comme composant Spring de la couche données.
 * Fournit des requêtes natives Oracle pour la résolution hiérarchique via les projets
 * et la détection des violations SLA.
 *
 * @since 1.0
 */
@Repository
public interface TrainingRequestRepository extends JpaRepository<TrainingRequest, Long> {

    /**
     * Retourne les demandes de formation d'un employé, triées par date de création décroissante.
     *
     * @param employeeId identifiant Oracle de l'employé
     * @return liste des demandes de formation de l'employé
     */
    List<TrainingRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    /**
     * Retourne les demandes de formation ayant un statut donné, triées par date décroissante.
     *
     * @param status valeur Oracle du statut (ex : {@code APPROUVE_CHEF}, {@code PLANIFIEE})
     * @return liste des demandes de formation correspondant au statut
     */
    List<TrainingRequest> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * Retourne les demandes de formation d'un statut donné pour les membres actifs d'un projet
     * créé par le chef donné (via {@code PROJECT_MEMBERS}).
     *
     * @param chefEmployeeId identifiant Oracle du chef (PROJECTS.CREATED_BY)
     * @param status         valeur Oracle du statut à filtrer (ex : {@code EN_ATTENTE})
     * @return liste filtrée par statut des demandes de formation des membres du projet
     */
    @Query(value = """
            SELECT tr.* FROM GERAI.TRAINING_REQUESTS tr
            WHERE tr.EMPLOYEE_ID IN (
              SELECT DISTINCT pm.EMPLOYEE_ID
              FROM GERAI.PROJECT_MEMBERS pm
              JOIN GERAI.PROJECTS p ON pm.PROJECT_ID = p.PROJECT_ID
              WHERE p.CREATED_BY = :chefEmployeeId
                AND pm.IS_ACTIVE = 1
            )
            AND tr.STATUS = :status
            ORDER BY tr.CREATED_AT DESC
            """, nativeQuery = true)
    List<TrainingRequest> findByManagerAndStatus(
            @Param("chefEmployeeId") Long chefEmployeeId,
            @Param("status") String status);

    /**
     * Retourne toutes les demandes de formation des membres de l'équipe du chef,
     * en couvrant deux sources de hiérarchie : EMPLOYEES.MANAGER_ID et PROJECT_MEMBERS.
     *
     * @param chefEmployeeId identifiant Oracle du chef
     * @return liste des demandes de formation des membres de l'équipe, triées par date décroissante
     */
    @Query(value = """
            SELECT tr.* FROM GERAI.TRAINING_REQUESTS tr
            WHERE tr.EMPLOYEE_ID IN (
              SELECT DISTINCT e.EMPLOYEE_ID
              FROM GERAI.EMPLOYEES e
              WHERE e.MANAGER_ID = :chefEmployeeId
                AND e.STATUS = 'ACTIF'
              UNION
              SELECT DISTINCT pm.EMPLOYEE_ID
              FROM GERAI.PROJECT_MEMBERS pm
              JOIN GERAI.PROJECTS p ON pm.PROJECT_ID = p.PROJECT_ID
              WHERE p.CREATED_BY = :chefEmployeeId
                AND pm.IS_ACTIVE = 1
            )
            ORDER BY tr.CREATED_AT DESC
            """, nativeQuery = true)
    List<TrainingRequest> findByManager(@Param("chefEmployeeId") Long chefEmployeeId);

    /**
     * Fallback département : retourne toutes les demandes de formation d'une liste d'employés.
     * Utilisé quand MANAGER_ID n'est pas renseigné et que la recherche par projet ne retourne rien.
     *
     * @param employeeIds liste des identifiants Oracle des employés du département
     * @return liste des demandes de formation de ces employés
     */
    List<TrainingRequest> findByEmployeeIdIn(List<Long> employeeIds);

    /**
     * Retourne les demandes de formation restées en attente depuis plus d'un seuil horaire donné
     * (violation SLA). Utilisé par {@link com.gerai.demandesservice.scheduler.SlaBreachScheduler}.
     *
     * @param seuil horodatage limite — toute demande créée avant ce seuil est considérée en violation SLA
     * @return liste des demandes de formation en violation SLA, triées par date de création croissante
     */
    @Query(value = """
            SELECT tr.* FROM GERAI.TRAINING_REQUESTS tr
            WHERE tr.STATUS = 'EN_ATTENTE'
              AND tr.CREATED_AT < :seuil
            ORDER BY tr.CREATED_AT ASC
            """, nativeQuery = true)
    List<TrainingRequest> findEnAttentePlusDe(@Param("seuil") LocalDateTime seuil);
}