package com.gerai.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité mappée sur CONVERSATIONS (V4__communication.sql).
 *
 * Différences avec l'ancienne entité :
 *  - PK : conversation_id (IDENTITY Oracle)
 *  - Plus de participant1Id/participant2Id fixes : les participants
 *    sont dans CONVERSATION_PARTICIPANTS (relation @OneToMany)
 *  - created_by : Long (employee_id Oracle), plus String Keycloak
 *  - last_message_at remplace date_dernier_message
 *  - Plus de colonnes DERNIER_MESSAGE / PARTICIPANT_X_NOM (pas dans la table Oracle)
 */
@Entity
@Table(name = "CONVERSATIONS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CONVERSATION_ID")
    private Long conversationId;

    @Column(name = "TYPE", nullable = false, length = 20)
    @Builder.Default
    private String type = "DIRECT";

    @Column(name = "NAME", length = 200)
    private String name;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    /** ID Oracle de l'employé créateur (FK EMPLOYEES) */
    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    @Column(name = "LAST_MESSAGE_AT")
    private LocalDateTime lastMessageAt;

    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Integer isActive = 1;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /** Participants (chargés à la demande pour éviter N+1) */
    @OneToMany(mappedBy = "conversation", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL)
    @Builder.Default
    private List<ConversationParticipant> participants = new ArrayList<>();
}