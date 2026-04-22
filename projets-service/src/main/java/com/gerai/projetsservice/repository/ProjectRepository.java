package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByCreatedByOrderByCreatedAtDesc(Long createdBy);

    @Query("""
            SELECT p FROM Project p
            WHERE EXISTS (
                SELECT 1 FROM ProjectMember m
                WHERE m.project = p
                  AND m.employeeId = :employeeId
                  AND m.isActive = 1
            )
            ORDER BY p.createdAt DESC
            """)
    List<Project> findByMemberEmployeeId(@Param("employeeId") Long employeeId);

    List<Project> findByDeptIdOrderByCreatedAtDesc(Long deptId);

    @Query("SELECT COUNT(p) FROM Project p WHERE p.status = :status")
    long countByStatus(@Param("status") String status);

    @Query("SELECT COUNT(p) FROM Project p")
    long countAll();

    /**
     * Recherche par nom exact (insensible à la casse).
     * Appelé depuis findByName() qui est consommé par taches-service via Feign.
     */
    Optional<Project> findByNameIgnoreCase(String name);
}