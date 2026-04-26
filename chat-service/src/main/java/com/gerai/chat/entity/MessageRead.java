package com.gerai.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entité mappée sur MESSAGE_READS (V4__communication.sql).
 *
 * Remplace l'ancien système de statut ENVOYE/LU dans l'entité Message.
 * La lecture est maintenant une relation many-to-many entre messages et employés.
 */
@Entity
@Table(name = "MESSAGE_READS",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mr_msg_emp",
                columnNames = {"MESSAGE_ID", "EMPLOYEE_ID"}
        ))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "READ_ID")
    private Long readId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MESSAGE_ID", nullable = false)
    private Message message;

    /** ID Oracle de l'employé qui a lu le message (FK EMPLOYEES) */
    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    @Column(name = "READ_AT", nullable = false)
    private LocalDateTime readAt;

    @PrePersist
    protected void onCreate() {
        this.readAt = LocalDateTime.now();
    }
}