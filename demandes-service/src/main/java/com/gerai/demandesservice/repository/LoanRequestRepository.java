package com.gerai.demandesservice.repository;

import com.gerai.demandesservice.model.LoanRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LoanRequestRepository extends JpaRepository<LoanRequest, Long> {

    List<LoanRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
    List<LoanRequest> findByStatusOrderByCreatedAtDesc(String status);
    List<LoanRequest> findByApprovedByOrderByCreatedAtDesc(Long approvedBy);
}