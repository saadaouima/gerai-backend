package com.gerai.demandesservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entité JPA représentant une demande de document administratif
 * (attestation de travail, bulletin de salaire, certificat de congé, etc.).
 * Mappée sur la table Oracle {@code GERAI.DOCUMENT_REQUESTS}.
 * <p>
 * Workflow : EN_ATTENTE → EN_COURS → PRET → LIVRE | REFUSE.
 * Le type de document est référencé par {@code docTypeId} (table {@code DOCUMENT_TYPES}).
 * <p>
 * Statuts valides (CHECK Oracle) :
 *   EN_ATTENTE | EN_COURS | PRET | LIVRE | REFUSE
 *
 * @since 1.0
 */
@Entity
@Table(name = "DOCUMENT_REQUESTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DocumentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REQUEST_ID")
    private Long requestId;

    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    /** FK → DOCUMENT_TYPES.doc_type_id — NUMBER NN */
    @Column(name = "DOC_TYPE_ID", nullable = false)
    private Long docTypeId;

    /** Motif ou détail — VARCHAR2(500) */
    @Column(name = "REASON", length = 500)
    private String reason;

    /** Nombre d'exemplaires — NUMBER(2) */
    @Column(name = "COPIES_COUNT")
    private Integer copiesCount;

    /** Langue du document — VARCHAR2(20) */
    @Column(name = "LANGUAGE", length = 20)
    @Builder.Default
    private String language = "FR";

    /**
     * VARCHAR2(20).
     * Valeurs : EN_ATTENTE | EN_COURS | PRET | LIVRE | REFUSE
     */
    @Column(name = "STATUS", nullable = false, length = 20)
    @Builder.Default
    private String status = "EN_ATTENTE";

    /** FK → EMPLOYEES.employee_id (agent RH qui traite) */
    @Column(name = "PROCESSED_BY")
    private Long processedBy;

    /** URL du document généré */
    @Column(name = "DOCUMENT_URL", length = 500)
    private String documentUrl;

    /** Horodatage de traitement de la demande par le service RH. */
    @Column(name = "PROCESSED_AT")
    private LocalDateTime processedAt;

    /** Horodatage de création de la demande — positionné par {@code @PrePersist}. */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Initialise {@code createdAt} à l'heure courante et le statut à {@code EN_ATTENTE} si null,
     * avant l'insertion JPA.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = "EN_ATTENTE";
    }
}