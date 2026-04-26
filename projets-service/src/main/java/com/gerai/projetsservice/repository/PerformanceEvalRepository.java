package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface PerformanceEvalRepository extends JpaRepository<PerformanceEval, Long> {

    List<PerformanceEval> findByEmployeeIdOrderByPeriodYearDesc(Long employeeId);

    List<PerformanceEval> findByEvaluatorIdOrderByCreatedAtDesc(Long evaluatorId);

    Optional<PerformanceEval> findByEmployeeIdAndPeriodYearAndPeriodQuarter(
            Long employeeId, Integer year, String quarter);
}
