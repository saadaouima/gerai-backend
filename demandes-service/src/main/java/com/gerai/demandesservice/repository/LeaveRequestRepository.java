// ═══════════════════════════════════════════════════════════════════════════
//  LeaveRequestRepository.java
// ═══════════════════════════════════════════════════════════════════════════
package com.gerai.demandesservice.repository;

import com.gerai.demandesservice.model.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(String status);

    List<LeaveRequest> findByStatusInOrderByCreatedAtDesc(List<String> statuses);

    /**
     * Toutes les demandes de congé en attente de validation Chef
     * pour les employés dont le manager est managerEmployeeId.
     */
    @Query(value = """
            SELECT lr.* FROM GERAI_USER.LEAVE_REQUESTS lr
            JOIN GERAI_USER.EMPLOYEES e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE e.MANAGER_ID = :managerEmployeeId
              AND lr.STATUS = :status
            ORDER BY lr.CREATED_AT DESC
            """, nativeQuery = true)
    List<LeaveRequest> findByManagerAndStatus(
            @Param("managerEmployeeId") Long managerEmployeeId,
            @Param("status") String status);

    /**
     * Toutes les demandes d'un manager (toutes statuts).
     */
    @Query(value = """
            SELECT lr.* FROM GERAI_USER.LEAVE_REQUESTS lr
            JOIN GERAI_USER.EMPLOYEES e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE e.MANAGER_ID = :managerEmployeeId
            ORDER BY lr.CREATED_AT DESC
            """, nativeQuery = true)
    List<LeaveRequest> findByManager(@Param("managerEmployeeId") Long managerEmployeeId);
}