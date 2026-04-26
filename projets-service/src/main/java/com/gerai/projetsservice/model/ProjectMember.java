package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "PROJECT_MEMBERS",
        uniqueConstraints = @UniqueConstraint(name = "uk_pm_proj_emp",
                columnNames = {"PROJECT_ID", "EMPLOYEE_ID"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MEMBER_ID")
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROJECT_ID", nullable = false)
    private Project project;

    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    @Column(name = "ROLE", length = 50)
    @Builder.Default
    private String role = "MEMBRE";

    @Column(name = "JOINED_AT", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Integer isActive = 1;

    @PrePersist
    protected void onCreate() { this.joinedAt = LocalDateTime.now(); }
}