package com.gerai.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entité JPA mappée sur la table {@code CONVERSATION_PARTICIPANTS}.
 * <p>
 * Représente l'appartenance d'un employé à une conversation. Cette table de jointure
 * remplace les anciens champs {@code participant1Id}/{@code participant2Id} de l'entité
 * {@link Conversation}, permettant ainsi les conversations de groupe avec N participants.
 * <p>
 * Une contrainte d'unicité ({@code uk_cp_conv_emp}) garantit qu'un employé
 * ne peut appartenir qu'une seule fois à la même conversation.
 * <p>
 * {@code @Entity} : déclare cette classe comme entité JPA persistée.
 * <br>
 * {@code @Table} : mappe sur la table Oracle {@code CONVERSATION_PARTICIPANTS}.
 * <br>
 * {@code @Data}, {@code @Builder} (Lombok) : génèrent les accesseurs et le pattern builder.
 *
 * @since 1.0
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

    /** Identifiant unique Oracle du participant (clé primaire auto-incrémentée). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PARTICIPANT_ID")
    private Long participantId;

    /**
     * Conversation à laquelle appartient ce participant.
     * Chargement paresseux pour éviter le problème N+1.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CONVERSATION_ID", nullable = false)
    private Conversation conversation;

    /** ID Oracle de l'employé participant (clé étrangère vers {@code EMPLOYEES}). */
    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    /**
     * Nom d'affichage du participant, non stocké en base de données.
     * Ce champ transient est calculé côté service via un JOIN avec {@code EMPLOYEES}
     * au moment du mapping DTO.
     */
    @Transient
    private String nomAffichage;

    /** Rôle dans la conversation : {@code ADMIN} (créateur de groupe) ou {@code MEMBRE}. */
    @Column(name = "ROLE", length = 20)
    @Builder.Default
    private String role = "MEMBRE";

    /** Horodatage d'adhésion à la conversation, renseigné automatiquement par {@link #onCreate()}. */
    @Column(name = "JOINED_AT", nullable = false)
    private LocalDateTime joinedAt;

    /** Horodatage de départ de la conversation (null = encore participant actif). */
    @Column(name = "LEFT_AT")
    private LocalDateTime leftAt;

    /** Indicateur de mise en sourdine des notifications : {@code 1} = sourdine activée, {@code 0} = notifications actives. */
    @Column(name = "IS_MUTED")
    @Builder.Default
    private Integer isMuted = 0;

    /** Horodatage de la dernière lecture de la conversation par ce participant. */
    @Column(name = "LAST_READ_AT")
    private LocalDateTime lastReadAt;

    /**
     * Initialise automatiquement l'horodatage d'adhésion avant la première persistance.
     */
    @PrePersist
    protected void onCreate() {
        this.joinedAt = LocalDateTime.now();
    }
}