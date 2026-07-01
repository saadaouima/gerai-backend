package com.gerai_backend.gerai.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entité JPA représentant un document personnel d'un employé (contrat, attestation, diplôme, etc.),
 * mappée sur la table {@code EMPLOYEE_DOCUMENTS} de la base Oracle 23ai.
 *
 * <p>@Entity : indique à Hibernate que cette classe est une entité JPA persistée en base.</p>
 * <p>@Table(name = "EMPLOYEE_DOCUMENTS") : spécifie le nom de la table Oracle cible.</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "EMPLOYEE_DOCUMENTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeeDocument {

    /** Clé primaire du document, générée par IDENTITY Oracle. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DOC_ID")
    private Long id;

    /** FK → {@code EMPLOYEES.employee_id} — identifiant de l'employé propriétaire du document. */
    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    /** Nom original du fichier tel que fourni lors du téléversement. */
    @Column(name = "NOM", nullable = false, length = 255)
    private String nom;

    /** Type MIME du fichier (ex. {@code application/pdf}, {@code image/png}). */
    @Column(name = "TYPE_FICHIER", length = 100)
    private String typeFichier;

    /** Taille du fichier en octets. */
    @Column(name = "TAILLE")
    private Long taille;

    /** Chemin absolu du fichier sur le système de fichiers du serveur. */
    @Column(name = "CHEMIN_FICHIER", nullable = false, length = 500)
    private String cheminFichier;

    /** Horodatage d'ajout du document (non modifiable après insertion). */
    @Column(name = "DATE_AJOUT", nullable = false, updatable = false)
    private LocalDateTime dateAjout;

    /**
     * Callback JPA exécuté avant chaque insertion en base.
     * Initialise {@code dateAjout} à l'horodatage courant.
     */
    @PrePersist
    protected void onCreate() {
        this.dateAjout = LocalDateTime.now();
    }
}
