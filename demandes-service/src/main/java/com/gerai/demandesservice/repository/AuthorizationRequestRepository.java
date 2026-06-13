package com.gerai.demandesservice.repository;

import com.gerai.demandesservice.model.AuthorizationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AuthorizationRequestRepository extends JpaRepository<AuthorizationRequest, Long> {

    /* ── REQUÊTES STANDARDS ── */

    List<AuthorizationRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    List<AuthorizationRequest> findByStatusOrderByCreatedAtDesc(String status);

    /* ── HIÉRARCHIE (MANAGER_ID) ── */

    @Query(value = """
            SELECT ar.* FROM GERAI.AUTHORIZATION_REQUESTS ar
            JOIN GERAI.EMPLOYEES e ON ar.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE e.MANAGER_ID = :chefId
            ORDER BY ar.CREATED_AT DESC
            """, nativeQuery = true)
    List<AuthorizationRequest> findByManagerViaHierarchy(@Param("chefId") Long chefId);

    @Query(value = """
            SELECT ar.* FROM GERAI.AUTHORIZATION_REQUESTS ar
            JOIN GERAI.EMPLOYEES e ON ar.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE e.MANAGER_ID = :chefId
              AND ar.STATUS = :status
            ORDER BY ar.CREATED_AT DESC
            """, nativeQuery = true)
    List<AuthorizationRequest> findByManagerViaHierarchyAndStatus(
            @Param("chefId") Long chefId,
            @Param("status") String status);

    /* ── PROJET (PROJECT_MEMBERS) ── */

    @Query(value = """
            SELECT ar.* FROM GERAI.AUTHORIZATION_REQUESTS ar
            WHERE ar.EMPLOYEE_ID IN (
              SELECT DISTINCT pm.EMPLOYEE_ID
              FROM GERAI.PROJECT_MEMBERS pm
              JOIN GERAI.PROJECTS p ON pm.PROJECT_ID = p.PROJECT_ID
              WHERE p.CREATED_BY = :chefId AND pm.IS_ACTIVE = 1
            )
            ORDER BY ar.CREATED_AT DESC
            """, nativeQuery = true)
    List<AuthorizationRequest> findByManagerViaProject(@Param("chefId") Long chefId);

    @Query(value = """
            SELECT ar.* FROM GERAI.AUTHORIZATION_REQUESTS ar
            WHERE ar.EMPLOYEE_ID IN (
              SELECT DISTINCT pm.EMPLOYEE_ID
              FROM GERAI.PROJECT_MEMBERS pm
              JOIN GERAI.PROJECTS p ON pm.PROJECT_ID = p.PROJECT_ID
              WHERE p.CREATED_BY = :chefId AND pm.IS_ACTIVE = 1
            )
              AND ar.STATUS = :status
            ORDER BY ar.CREATED_AT DESC
            """, nativeQuery = true)
    List<AuthorizationRequest> findByManagerViaProjectAndStatus(
            @Param("chefId") Long chefId,
            @Param("status") String status);

    /* ── Compatibilité appels existants — merge hiérarchie + projet ── */

    default List<AuthorizationRequest> findByManager(Long chefId) {
        java.util.Map<Long, AuthorizationRequest> seen = new java.util.LinkedHashMap<>();
        findByManagerViaHierarchy(chefId).forEach(e -> seen.put(e.getRequestId(), e));
        findByManagerViaProject(chefId).forEach(e -> seen.putIfAbsent(e.getRequestId(), e));
        return new java.util.ArrayList<>(seen.values());
    }

    default List<AuthorizationRequest> findByManager(Long chefId, String status) {
        java.util.Map<Long, AuthorizationRequest> seen = new java.util.LinkedHashMap<>();
        findByManagerViaHierarchyAndStatus(chefId, status).forEach(e -> seen.put(e.getRequestId(), e));
        findByManagerViaProjectAndStatus(chefId, status).forEach(e -> seen.putIfAbsent(e.getRequestId(), e));
        return new java.util.ArrayList<>(seen.values());
    }

    default List<AuthorizationRequest> findByManagerAndStatus(Long chefId, String status) {
        return findByManager(chefId, status);
    }

    /** Fallback dept-based: toutes les demandes pour une liste d'employés */
    List<AuthorizationRequest> findByEmployeeIdIn(java.util.List<Long> employeeIds);
}
