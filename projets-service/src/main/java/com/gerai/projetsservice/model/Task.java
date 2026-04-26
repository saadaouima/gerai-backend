package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "TASKS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TASK_ID")
    private Long taskId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROJECT_ID", nullable = false)
    private Project project;

    @Column(name = "TITLE", nullable = false, length = 300)
    private String title;

    @Lob
    @Column(name = "DESCRIPTION")
    private String description;

    /** FK EMPLOYEES — employé assigné */
    @Column(name = "ASSIGNED_TO")
    private Long assignedTo;

    /** FK EMPLOYEES — créateur de la tâche */
    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    /** Auto-référence pour sous-tâches */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PARENT_TASK_ID")
    private Task parentTask;

    @Column(name = "PRIORITY", length = 20)
    @Builder.Default
    private String priority = "NORMALE";

    @Column(name = "STATUS", nullable = false, length = 30)
    @Builder.Default
    private String status = "A_FAIRE";

    @Column(name = "PROGRESS_PCT")
    @Builder.Default
    private Integer progressPct = 0;

    @Column(name = "DUE_DATE")
    private LocalDate dueDate;

    @Column(name = "ESTIMATED_HOURS")
    private Double estimatedHours;

    @Column(name = "ACTUAL_HOURS")
    private Double actualHours;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}