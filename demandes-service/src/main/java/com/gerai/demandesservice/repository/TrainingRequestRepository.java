package com.gerai.demandesservice.repository;

import com.gerai.demandesservice.model.TrainingRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TrainingRequestRepository extends JpaRepository<TrainingRequest, Long> {

    List<TrainingRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
    List<TrainingRequest> findByStatusOrderByCreatedAtDesc(String status);

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

    @Query(value = """
            SELECT tr.* FROM GERAI.TRAINING_REQUESTS tr
            WHERE tr.EMPLOYEE_ID IN (
              SELECT DISTINCT pm.EMPLOYEE_ID
              FROM GERAI.PROJECT_MEMBERS pm
              JOIN GERAI.PROJECTS p ON pm.PROJECT_ID = p.PROJECT_ID
              WHERE p.CREATED_BY = :chefEmployeeId
                AND pm.IS_ACTIVE = 1
            )
            ORDER BY tr.CREATED_AT DESC
            """, nativeQuery = true)
    List<TrainingRequest> findByManager(@Param("chefEmployeeId") Long chefEmployeeId);

    /** Fallback dept-based: toutes les demandes pour une liste d'employés */
    List<TrainingRequest> findByEmployeeIdIn(List<Long> employeeIds);

    /** Demandes en attente depuis plus de X heures (SLA breach). */
    @Query(value = """
            SELECT tr.* FROM GERAI.TRAINING_REQUESTS tr
            WHERE tr.STATUS = 'EN_ATTENTE'
              AND tr.CREATED_AT < :seuil
            ORDER BY tr.CREATED_AT ASC
            """, nativeQuery = true)
    List<TrainingRequest> findEnAttentePlusDe(@Param("seuil") LocalDateTime seuil);
}