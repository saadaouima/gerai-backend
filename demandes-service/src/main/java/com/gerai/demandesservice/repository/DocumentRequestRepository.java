package com.gerai.demandesservice.repository;

import com.gerai.demandesservice.model.DocumentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DocumentRequestRepository extends JpaRepository<DocumentRequest, Long> {

    List<DocumentRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
    List<DocumentRequest> findByStatusOrderByCreatedAtDesc(String status);
    List<DocumentRequest> findByProcessedByOrderByCreatedAtDesc(Long processedBy);
}
