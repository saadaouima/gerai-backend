package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    List<ProjectMember> findByProject_ProjectId(Long projectId);

    Optional<ProjectMember> findByProject_ProjectIdAndEmployeeId(Long projectId, Long employeeId);

    @Query("""
            SELECT pm FROM ProjectMember pm
            WHERE pm.project.projectId = :projectId
              AND pm.isActive = 1
            """)
    List<ProjectMember> findActiveByProjectId(@Param("projectId") Long projectId);

    void deleteByProject_ProjectIdAndEmployeeId(Long projectId, Long employeeId);

    @Query("""
            SELECT pm FROM ProjectMember pm
            JOIN FETCH pm.project
            WHERE pm.employeeId = :employeeId
              AND pm.isActive = 1
            """)
    List<ProjectMember> findActiveByEmployeeId(@Param("employeeId") Long employeeId);
}