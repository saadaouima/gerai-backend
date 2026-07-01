package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entité JPA représentant un commentaire posté sur une tâche par un employé.
 * <p>
 * Permet aux membres d'un projet d'échanger des informations contextuelles
 * directement sur une tâche. Chaque commentaire est horodaté à sa création.
 * </p>
 * <p>
 * {@code @Entity} : classe persistée en base Oracle.<br>
 * {@code @Table(name = "TASK_COMMENTS")} : nom de la table Oracle.<br>
 * {@code @PrePersist} : initialise {@code createdAt} à la date courante avant la première persistance.
 * </p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "TASK_COMMENTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskComment {

    /** Identifiant unique du commentaire (clé primaire générée). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COMMENT_ID")
    private Long commentId;

    /** Référence à la tâche commentée (chargement différé). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TASK_ID", nullable = false)
    private Task task;

    /** Identifiant Oracle de l'employé auteur du commentaire (FK EMPLOYEES). */
    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    /** Contenu textuel du commentaire (stocké en CLOB Oracle). */
    @Lob
    @Column(name = "CONTENT", nullable = false)
    private String content;

    /** Date et heure de création du commentaire (initialisée automatiquement, non modifiable). */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Initialise la date de création avant la première persistance.
     */
    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }
}
