package com.gerai.demandesservice.repository;

import com.gerai.demandesservice.model.TrainingRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TrainingRequestRepository extends JpaRepository<TrainingRequest, Long> {

    List<TrainingRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
    List<TrainingRequest> findByStatusOrderByCreatedAtDesc(String status);

    @Query(value = """
            SELECT tr.* FROM GERAI_USER.TRAINING_REQUESTS tr
            JOIN GERAI_USER.EMPLOYEES e ON tr.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE e.MANAGER_ID = :managerEmployeeId
              AND tr.STATUS = :status
            ORDER BY tr.CREATED_AT DESC
            """, nativeQuery = true)
    List<TrainingRequest> findByManagerAndStatus(
            @Param("managerEmployeeId") Long managerEmployeeId,
            @Param("status") String status);

    @Query(value = """
            SELECT tr.* FROM GERAI_USER.TRAINING_REQUESTS tr
            JOIN GERAI_USER.EMPLOYEES e ON tr.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE e.MANAGER_ID = :managerEmployeeId
            ORDER BY tr.CREATED_AT DESC
            """, nativeQuery = true)
    List<TrainingRequest> findByManager(@Param("managerEmployeeId") Long managerEmployeeId);
}