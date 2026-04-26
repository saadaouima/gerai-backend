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

    /* ── REQUÊTES NATIVES POUR L'ESPACE CHEF ── */

    /**
     * Utilisé dans getDemandesEquipe (toutes les demandes de l'équipe)
     */
    @Query(value = """
            SELECT ar.* FROM GERAI_USER.AUTHORIZATION_REQUESTS ar
            JOIN GERAI_USER.EMPLOYEES e ON ar.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE e.MANAGER_ID = :managerEmployeeId
            ORDER BY ar.CREATED_AT DESC
            """, nativeQuery = true)
    List<AuthorizationRequest> findByManager(@Param("managerEmployeeId") Long managerEmployeeId);

    /**
     * Surcharge pour supporter l'appel à 2 paramètres dans DemandeService (ligne 155)
     */
    @Query(value = """
            SELECT ar.* FROM GERAI_USER.AUTHORIZATION_REQUESTS ar
            JOIN GERAI_USER.EMPLOYEES e ON ar.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE e.MANAGER_ID = :managerEmployeeId
              AND ar.STATUS = :status
            ORDER BY ar.CREATED_AT DESC
            """, nativeQuery = true)
    List<AuthorizationRequest> findByManager(
            @Param("managerEmployeeId") Long managerEmployeeId,
            @Param("status") String status);

    /**
     * Utilisé dans getDemandesEnAttenteChef (ligne 171)
     */
    @Query(value = """
            SELECT ar.* FROM GERAI_USER.AUTHORIZATION_REQUESTS ar
            JOIN GERAI_USER.EMPLOYEES e ON ar.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE e.MANAGER_ID = :managerEmployeeId
              AND ar.STATUS = :status
            ORDER BY ar.CREATED_AT DESC
            """, nativeQuery = true)
    List<AuthorizationRequest> findByManagerAndStatus(
            @Param("managerEmployeeId") Long managerEmployeeId,
            @Param("status") String status);
}