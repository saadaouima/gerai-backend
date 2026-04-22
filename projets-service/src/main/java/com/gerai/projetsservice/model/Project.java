package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/* ═══════════════════════════════════════════════════════
   PROJECT
   ═══════════════════════════════════════════════════════ */

@Entity
@Table(name = "PROJECTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PROJECT_ID")
    private Long projectId;

    @Column(name = "NAME", nullable = false, length = 200)
    private String name;

    @Lob
    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "CODE", length = 30)
    private String code;

    /** FK EMPLOYEES.employee_id — créateur du projet (Chef) */
    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    /** FK DEPARTMENTS.dept_id */
    @Column(name = "DEPT_ID")
    private Long deptId;

    @Column(name = "START_DATE")
    private LocalDate startDate;

    @Column(name = "END_DATE")
    private LocalDate endDate;

    @Column(name = "PRIORITY", length = 20)
    @Builder.Default
    private String priority = "NORMALE";

    @Column(name = "STATUS", nullable = false, length = 30)
    @Builder.Default
    private String status = "EN_COURS";

    @Column(name = "PROGRESS_PCT")
    @Builder.Default
    private Integer progressPct = 0;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<ProjectMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<Task> tasks = new ArrayList<>();

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}