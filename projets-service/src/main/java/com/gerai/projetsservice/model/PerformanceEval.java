package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "PERFORMANCE_EVALS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PerformanceEval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EVAL_ID")
    private Long evalId;

    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    @Column(name = "EVALUATOR_ID", nullable = false)
    private Long evaluatorId;

    @Column(name = "PERIOD_YEAR", nullable = false)
    private Integer periodYear;

    @Column(name = "PERIOD_QUARTER", length = 5)
    private String periodQuarter;

    @Column(name = "SCORE")
    private Double score;

    @Lob @Column(name = "CRITERIA_SCORES") private String criteriaScores;
    @Lob @Column(name = "STRENGTHS")       private String strengths;
    @Lob @Column(name = "IMPROVEMENTS")    private String improvements;
    @Lob @Column(name = "COMMENTS")        private String comments;

    @Column(name = "STATUS", nullable = false, length = 20)
    @Builder.Default
    private String status = "BROUILLON";

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }
}