package com.gerai.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entité mappée sur CONVERSATION_PARTICIPANTS (V4__communication.sql).
 *
 * Remplace les anciens champs participant1Id/participant2Id
 * de l'entité Conversation : les participants sont maintenant
 * dans une table de jointure dédiée, ce qui permet les groupes.
 */
@Entity
@Table(name = "CONVERSATION_PARTICIPANTS",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cp_conv_emp",
                columnNames = {"CONVERSATION_ID", "EMPLOYEE_ID"}
        ))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PARTICIPANT_ID")
    private Long participantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CONVERSATION_ID", nullable = false)
    private Conversation conversation;

    /** ID Oracle de l'employé participant (FK EMPLOYEES) */
    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    /**
     * Nom d'affichage du participant (non stocké en DB — calculé côté service).
     * Ce champ est @Transient : il vient du JOIN avec EMPLOYEES au moment du mapping DTO.
     */
    @Transient
    private String nomAffichage;

    @Column(name = "ROLE", length = 20)
    @Builder.Default
    private String role = "MEMBRE";

    @Column(name = "JOINED_AT", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "LEFT_AT")
    private LocalDateTime leftAt;

    @Column(name = "IS_MUTED")
    @Builder.Default
    private Integer isMuted = 0;

    @Column(name = "LAST_READ_AT")
    private LocalDateTime lastReadAt;

    @PrePersist
    protected void onCreate() {
        this.joinedAt = LocalDateTime.now();
    }
}