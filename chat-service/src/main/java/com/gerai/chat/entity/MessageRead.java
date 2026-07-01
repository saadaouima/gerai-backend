package com.gerai.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entité JPA mappée sur la table {@code MESSAGE_READS}.
 * <p>
 * Implémente le système de tracking de lecture des messages.
 * Remplace l'ancien statut binaire {@code ENVOYE}/{@code LU} de l'entité {@link Message} :
 * chaque ligne représente la lecture d'un message par un employé spécifique,
 * permettant ainsi de gérer la lecture par participant dans les groupes.
 * <p>
 * Une contrainte d'unicité ({@code uk_mr_msg_emp}) garantit qu'un employé
 * ne peut avoir qu'un seul enregistrement de lecture par message.
 * <p>
 * La table est alimentée par une requête {@code INSERT ... SELECT} native
 * dans {@link com.gerai.chat.repository.MessageReadRepository#markAllAsRead}
 * lors de l'ouverture d'une conversation.
 * <p>
 * {@code @Entity} : déclare cette classe comme entité JPA persistée.
 * <br>
 * {@code @Table(name = "MESSAGE_READS")} : mappe sur la table Oracle correspondante.
 *
 * @since 1.0
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

    /** Identifiant unique Oracle de l'enregistrement de lecture (clé primaire auto-incrémentée). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "READ_ID")
    private Long readId;

    /**
     * Message qui a été lu.
     * Chargement paresseux pour éviter les requêtes inutiles lors des comptages.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MESSAGE_ID", nullable = false)
    private Message message;

    /** ID Oracle de l'employé qui a lu le message (clé étrangère vers {@code EMPLOYEES}). */
    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    /** Horodatage de lecture du message, renseigné automatiquement par {@link #onCreate()}. */
    @Column(name = "READ_AT", nullable = false)
    private LocalDateTime readAt;

    /**
     * Initialise automatiquement l'horodatage de lecture avant la première persistance.
     */
    @PrePersist
    protected void onCreate() {
        this.readAt = LocalDateTime.now();
    }
}