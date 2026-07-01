package com.gerai.demandesservice.repository;

import com.gerai.demandesservice.model.LoanRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository Spring Data JPA pour les demandes de prêt/crédit
 * (table {@code GERAI.LOAN_REQUESTS}).
 * <p>
 * {@code @Repository} : marque cette interface comme composant Spring de la couche données.
 * Fournit des requêtes natives Oracle pour la détection des violations SLA.
 *
 * @since 1.0
 */
@Repository
public interface LoanRequestRepository extends JpaRepository<LoanRequest, Long> {

    /**
     * Retourne les demandes de crédit d'un employé, triées par date de création décroissante.
     *
     * @param employeeId identifiant Oracle de l'employé
     * @return liste des demandes de crédit de l'employé
     */
    List<LoanRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    /**
     * Retourne les demandes de crédit ayant un statut donné, triées par date décroissante.
     *
     * @param status valeur Oracle du statut (ex : {@code EN_ETUDE_DG}, {@code APPROUVE})
     * @return liste des demandes correspondant au statut
     */
    List<LoanRequest> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * Retourne les demandes de crédit dont le statut est dans une liste donnée, triées par date décroissante.
     *
     * @param statuses liste des valeurs Oracle de statuts recherchés
     * @return liste des demandes correspondant à l'un des statuts
     */
    List<LoanRequest> findByStatusInOrderByCreatedAtDesc(List<String> statuses);

    /**
     * Retourne les demandes de crédit approuvées par un valideur donné, triées par date décroissante.
     *
     * @param approvedBy identifiant Oracle du valideur (EMPLOYEES.EMPLOYEE_ID)
     * @return liste des demandes approuvées par ce valideur
     */
    List<LoanRequest> findByApprovedByOrderByCreatedAtDesc(Long approvedBy);

    /**
     * Retourne les demandes de crédit restées en attente depuis plus d'un seuil horaire donné
     * (violation SLA). Utilisé par {@link com.gerai.demandesservice.scheduler.SlaBreachScheduler}.
     *
     * @param seuil horodatage limite — toute demande créée avant ce seuil est considérée en violation SLA
     * @return liste des demandes de crédit en violation SLA, triées par date de création croissante
     */
    @Query(value = """
            SELECT lr.* FROM GERAI.LOAN_REQUESTS lr
            WHERE lr.STATUS = 'EN_ATTENTE'
              AND lr.CREATED_AT < :seuil
            ORDER BY lr.CREATED_AT ASC
            """, nativeQuery = true)
    List<LoanRequest> findEnAttentePlusDe(@Param("seuil") LocalDateTime seuil);

    /* ── Chef — hiérarchie (MANAGER_ID) ── */

    @Query(value = """
            SELECT lr.* FROM GERAI.LOAN_REQUESTS lr
            JOIN GERAI.EMPLOYEES e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE e.MANAGER_ID = :chefId
            ORDER BY lr.CREATED_AT DESC
            """, nativeQuery = true)
    List<LoanRequest> findByManagerViaHierarchy(@Param("chefId") Long chefId);

    @Query(value = """
            SELECT lr.* FROM GERAI.LOAN_REQUESTS lr
            JOIN GERAI.EMPLOYEES e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE e.MANAGER_ID = :chefId
              AND lr.STATUS = :status
            ORDER BY lr.CREATED_AT DESC
            """, nativeQuery = true)
    List<LoanRequest> findByManagerViaHierarchyAndStatus(
            @Param("chefId") Long chefId,
            @Param("status") String status);

    /* ── Chef — projet (PROJECT_MEMBERS) ── */

    @Query(value = """
            SELECT lr.* FROM GERAI.LOAN_REQUESTS lr
            WHERE lr.EMPLOYEE_ID IN (
              SELECT DISTINCT pm.EMPLOYEE_ID
              FROM GERAI.PROJECT_MEMBERS pm
              JOIN GERAI.PROJECTS p ON pm.PROJECT_ID = p.PROJECT_ID
              WHERE p.CREATED_BY = :chefId AND pm.IS_ACTIVE = 1
            )
            ORDER BY lr.CREATED_AT DESC
            """, nativeQuery = true)
    List<LoanRequest> findByManagerViaProject(@Param("chefId") Long chefId);

    @Query(value = """
            SELECT lr.* FROM GERAI.LOAN_REQUESTS lr
            WHERE lr.EMPLOYEE_ID IN (
              SELECT DISTINCT pm.EMPLOYEE_ID
              FROM GERAI.PROJECT_MEMBERS pm
              JOIN GERAI.PROJECTS p ON pm.PROJECT_ID = p.PROJECT_ID
              WHERE p.CREATED_BY = :chefId AND pm.IS_ACTIVE = 1
            )
              AND lr.STATUS = :status
            ORDER BY lr.CREATED_AT DESC
            """, nativeQuery = true)
    List<LoanRequest> findByManagerViaProjectAndStatus(
            @Param("chefId") Long chefId,
            @Param("status") String status);

    /* ── Merge hiérarchie + projet (interface par défaut) ── */

    default List<LoanRequest> findByManager(Long chefId) {
        java.util.Map<Long, LoanRequest> seen = new java.util.LinkedHashMap<>();
        findByManagerViaHierarchy(chefId).forEach(e -> seen.put(e.getRequestId(), e));
        findByManagerViaProject(chefId).forEach(e -> seen.putIfAbsent(e.getRequestId(), e));
        return new java.util.ArrayList<>(seen.values());
    }

    default List<LoanRequest> findByManagerAndStatus(Long chefId, String status) {
        java.util.Map<Long, LoanRequest> seen = new java.util.LinkedHashMap<>();
        findByManagerViaHierarchyAndStatus(chefId, status).forEach(e -> seen.put(e.getRequestId(), e));
        findByManagerViaProjectAndStatus(chefId, status).forEach(e -> seen.putIfAbsent(e.getRequestId(), e));
        return new java.util.ArrayList<>(seen.values());
    }

    /* ── Fallback département ── */

    List<LoanRequest> findByEmployeeIdIn(java.util.List<Long> employeeIds);
}