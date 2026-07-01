package com.gerai.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité JPA mappée sur la table {@code CONVERSATIONS}.
 * <p>
 * Représente une conversation de messagerie, qui peut être de type :
 * <ul>
 *   <li>{@code DIRECT} : conversation entre exactement 2 employés.</li>
 *   <li>{@code GROUPE} : conversation avec plusieurs participants et un nom.</li>
 *   <li>{@code ANNONCE} : diffusion à sens unique (non encore implémentée).</li>
 * </ul>
 * Les participants sont modélisés dans la table {@code CONVERSATION_PARTICIPANTS}
 * (relation {@code @OneToMany}), ce qui permet les groupes et évite les colonnes fixes
 * {@code participant1Id}/{@code participant2Id} de l'ancienne architecture.
 * <p>
 * {@code @Entity} : déclare cette classe comme entité JPA persistée.
 * <br>
 * {@code @Table(name = "CONVERSATIONS")} : mappe sur la table Oracle correspondante.
 * <br>
 * {@code @Data} (Lombok) : génère getters, setters, {@code equals}, {@code hashCode} et {@code toString}.
 * <br>
 * {@code @Builder} (Lombok) : fournit le pattern builder pour la construction immutable.
 *
 * @since 1.0
 */
@Entity
@Table(name = "CONVERSATIONS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    /** Identifiant unique Oracle de la conversation (clé primaire auto-incrémentée). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CONVERSATION_ID")
    private Long conversationId;

    /** Type de conversation : {@code DIRECT}, {@code GROUPE} ou {@code ANNONCE}. */
    @Column(name = "TYPE", nullable = false, length = 20)
    @Builder.Default
    private String type = "DIRECT";

    /** Nom de la conversation (obligatoire pour les groupes, null pour les conversations directes). */
    @Column(name = "NAME", length = 200)
    private String name;

    /** Description optionnelle du groupe (null pour les conversations directes). */
    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    /** ID Oracle de l'employé créateur de la conversation (clé étrangère vers {@code EMPLOYEES}). */
    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    /** Horodatage du dernier message envoyé, utilisé pour le tri de la liste de conversations. */
    @Column(name = "LAST_MESSAGE_AT")
    private LocalDateTime lastMessageAt;

    /** Indicateur d'activité : {@code 1} = conversation active, {@code 0} = archivée. */
    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Integer isActive = 1;

    /** Horodatage de création de la conversation, renseigné automatiquement par {@link #onCreate()}. */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Initialise automatiquement l'horodatage de création avant la première persistance.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Liste des participants de la conversation.
     * Chargement paresseux ({@code LAZY}) pour éviter le problème N+1 lors de la liste des conversations.
     * Cascade {@code ALL} : la suppression d'une conversation entraîne la suppression de ses participants.
     */
    @OneToMany(mappedBy = "conversation", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL)
    @Builder.Default
    private List<ConversationParticipant> participants = new ArrayList<>();
}