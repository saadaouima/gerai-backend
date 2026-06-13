package com.gerai.demandesservice.repository;

import com.gerai.demandesservice.model.LoanRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoanRequestRepository extends JpaRepository<LoanRequest, Long> {

    List<LoanRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
    List<LoanRequest> findByStatusOrderByCreatedAtDesc(String status);
    List<LoanRequest> findByStatusInOrderByCreatedAtDesc(List<String> statuses);
    List<LoanRequest> findByApprovedByOrderByCreatedAtDesc(Long approvedBy);

    /** Demandes en attente depuis plus de X heures (SLA breach). */
    @Query(value = """
            SELECT lr.* FROM GERAI.LOAN_REQUESTS lr
            WHERE lr.STATUS = 'EN_ATTENTE'
              AND lr.CREATED_AT < :seuil
            ORDER BY lr.CREATED_AT ASC
            """, nativeQuery = true)
    List<LoanRequest> findEnAttentePlusDe(@Param("seuil") LocalDateTime seuil);
}