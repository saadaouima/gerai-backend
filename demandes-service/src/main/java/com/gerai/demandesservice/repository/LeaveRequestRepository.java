// ═══════════════════════════════════════════════════════════════════════════
//  LeaveRequestRepository.java
// ═══════════════════════════════════════════════════════════════════════════
package com.gerai.demandesservice.repository;

import com.gerai.demandesservice.model.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(String status);

    List<LeaveRequest> findByStatusInOrderByCreatedAtDesc(List<String> statuses);

    /* ── Hiérarchie (MANAGER_ID) ── */

    @Query(value = """
            SELECT lr.* FROM GERAI.LEAVE_REQUESTS lr
            JOIN GERAI.EMPLOYEES e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE e.MANAGER_ID = :chefId
            ORDER BY lr.CREATED_AT DESC
            """, nativeQuery = true)
    List<LeaveRequest> findByManagerViaHierarchy(@Param("chefId") Long chefId);

    @Query(value = """
            SELECT lr.* FROM GERAI.LEAVE_REQUESTS lr
            JOIN GERAI.EMPLOYEES e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE e.MANAGER_ID = :chefId
              AND lr.STATUS = :status
            ORDER BY lr.CREATED_AT DESC
            """, nativeQuery = true)
    List<LeaveRequest> findByManagerViaHierarchyAndStatus(
            @Param("chefId") Long chefId,
            @Param("status") String status);

    /* ── Projet (PROJECT_MEMBERS) ── */

    @Query(value = """
            SELECT lr.* FROM GERAI.LEAVE_REQUESTS lr
            WHERE lr.EMPLOYEE_ID IN (
              SELECT DISTINCT pm.EMPLOYEE_ID
              FROM GERAI.PROJECT_MEMBERS pm
              JOIN GERAI.PROJECTS p ON pm.PROJECT_ID = p.PROJECT_ID
              WHERE p.CREATED_BY = :chefId AND pm.IS_ACTIVE = 1
            )
            ORDER BY lr.CREATED_AT DESC
            """, nativeQuery = true)
    List<LeaveRequest> findByManagerViaProject(@Param("chefId") Long chefId);

    @Query(value = """
            SELECT lr.* FROM GERAI.LEAVE_REQUESTS lr
            WHERE lr.EMPLOYEE_ID IN (
              SELECT DISTINCT pm.EMPLOYEE_ID
              FROM GERAI.PROJECT_MEMBERS pm
              JOIN GERAI.PROJECTS p ON pm.PROJECT_ID = p.PROJECT_ID
              WHERE p.CREATED_BY = :chefId AND pm.IS_ACTIVE = 1
            )
              AND lr.STATUS = :status
            ORDER BY lr.CREATED_AT DESC
            """, nativeQuery = true)
    List<LeaveRequest> findByManagerViaProjectAndStatus(
            @Param("chefId") Long chefId,
            @Param("status") String status);

    /* ── Compatibilité appels existants — merge hiérarchie + projet ── */

    default List<LeaveRequest> findByManager(Long chefId) {
        java.util.Map<Long, LeaveRequest> seen = new java.util.LinkedHashMap<>();
        findByManagerViaHierarchy(chefId).forEach(e -> seen.put(e.getRequestId(), e));
        findByManagerViaProject(chefId).forEach(e -> seen.putIfAbsent(e.getRequestId(), e));
        return new java.util.ArrayList<>(seen.values());
    }

    default List<LeaveRequest> findByManagerAndStatus(Long chefId, String status) {
        java.util.Map<Long, LeaveRequest> seen = new java.util.LinkedHashMap<>();
        findByManagerViaHierarchyAndStatus(chefId, status).forEach(e -> seen.put(e.getRequestId(), e));
        findByManagerViaProjectAndStatus(chefId, status).forEach(e -> seen.putIfAbsent(e.getRequestId(), e));
        return new java.util.ArrayList<>(seen.values());
    }

    /* ── Calendrier équipe ── */

    @Query(value = """
            SELECT lr.* FROM GERAI.LEAVE_REQUESTS lr
            JOIN GERAI.EMPLOYEES e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE e.MANAGER_ID = :chefId
              AND lr.STATUS NOT IN ('REFUSE', 'ANNULE')
              AND lr.START_DATE <= TO_DATE(:dateFin,  'YYYY-MM-DD')
              AND lr.END_DATE   >= TO_DATE(:dateDebut, 'YYYY-MM-DD')
            ORDER BY lr.START_DATE
            """, nativeQuery = true)
    List<LeaveRequest> findByManagerAndDateRange(
            @Param("chefId")    Long   chefId,
            @Param("dateDebut") String dateDebut,
            @Param("dateFin")   String dateFin);

    /**
     * Somme des jours de congé approuvés par RH pour un employé dans une année.
     */
    @Query(value = """
            SELECT NVL(SUM(DAYS_COUNT), 0)
            FROM GERAI.LEAVE_REQUESTS
            WHERE EMPLOYEE_ID = :employeeId
              AND STATUS = 'VALIDE_RH'
              AND EXTRACT(YEAR FROM START_DATE) = :year
            """, nativeQuery = true)
    BigDecimal sumApprovedDaysByYear(
            @Param("employeeId") Long employeeId,
            @Param("year")       int  year);

    /** Nombre de demandes de congé en attente pour un employé. */
    long countByEmployeeIdAndStatus(Long employeeId, String status);

    /** Somme des jours en cours (pending + approved) pour un type donné dans une année — pour quota check. */
    @Query(value = """
            SELECT NVL(SUM(DAYS_COUNT), 0)
            FROM GERAI.LEAVE_REQUESTS
            WHERE EMPLOYEE_ID    = :employeeId
              AND LEAVE_TYPE_ID  = :leaveTypeId
              AND STATUS NOT IN ('REFUSE', 'ANNULE')
              AND EXTRACT(YEAR FROM START_DATE) = :year
            """, nativeQuery = true)
    BigDecimal sumPendingAndApprovedDaysByTypeAndYear(
            @Param("employeeId")   Long employeeId,
            @Param("leaveTypeId")  Long leaveTypeId,
            @Param("year")         int  year);

    List<LeaveRequest> findByStatus(String status);

    /** Fallback dept-based: toutes les demandes pour une liste d'employés */
    List<LeaveRequest> findByEmployeeIdIn(java.util.List<Long> employeeIds);

    /** Demandes en attente depuis plus de X heures (SLA breach). */
    @Query(value = """
            SELECT lr.* FROM GERAI.LEAVE_REQUESTS lr
            WHERE lr.STATUS = 'EN_ATTENTE'
              AND lr.CREATED_AT < :seuil
            ORDER BY lr.CREATED_AT ASC
            """, nativeQuery = true)
    List<LeaveRequest> findEnAttentePlusDe(@Param("seuil") LocalDateTime seuil);
}