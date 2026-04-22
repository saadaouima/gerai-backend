package com.gerai.analyticsservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Table agrégée ABSENCE_STATS — une ligne par (employé, année, mois).
 *
 * DDL Oracle à exécuter si la table n'existe pas encore :
 * ─────────────────────────────────────────────────────
 *   CREATE TABLE ABSENCE_STATS (
 *       STAT_ID          NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 *       EMPLOYE_ID       NUMBER        NOT NULL,
 *       EMPLOYE_NOM      VARCHAR2(150),
 *       ANNEE            NUMBER(4)     NOT NULL,
 *       MOIS             NUMBER(2)     NOT NULL,
 *       NB_JOURS_CONGE   NUMBER(6,2)   DEFAULT 0 NOT NULL,
 *       NB_DEMANDES      NUMBER(5)     DEFAULT 0 NOT NULL,
 *       NB_VALIDEES      NUMBER(5)     DEFAULT 0 NOT NULL,
 *       NB_REFUSEES      NUMBER(5)     DEFAULT 0 NOT NULL,
 *       DATE_MISE_A_JOUR TIMESTAMP     DEFAULT SYSDATE,
 *       CONSTRAINT uk_absence UNIQUE (EMPLOYE_ID, ANNEE, MOIS)
 *   );
 * ─────────────────────────────────────────────────────
 *
 * CORRECTION : la PK est STAT_ID (nom explicite, cohérent avec le DDL Oracle).
 * CORRECTION : NB_JOURS_CONGE est Double car une journée peut être fractionnée
 *              (demi-journée). columnDefinition = "NUMBER" force le bon type Oracle.
 */
@Entity
@Table(name = "ABSENCE_STATS",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_absence",
                columnNames = {"EMPLOYE_ID", "ANNEE", "MOIS"}
        ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class AbsenceStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "STAT_ID")
    private Long id;

    @Column(name = "EMPLOYE_ID", nullable = false)
    private Long employeId;

    @Column(name = "EMPLOYE_NOM", length = 150)
    private String employeNom;

    /** Année de la période. Ex : 2026 */
    @Column(name = "ANNEE", nullable = false)
    private Integer annee;

    /** Mois de la période : 1 = Janvier … 12 = Décembre */
    @Column(name = "MOIS", nullable = false)
    private Integer mois;

    /**
     * Jours de congé validés ce mois.
     * Double pour supporter les demi-journées.
     * columnDefinition force Oracle à mapper NUMBER → Double sans erreur Hibernate.
     */
    @Builder.Default
    @Column(name = "NB_JOURS_CONGE", nullable = false, columnDefinition = "NUMBER")
    private Double nbJoursConge = 0.0;

    /** Nombre total de demandes soumises ce mois (tous types) */
    @Builder.Default
    @Column(name = "NB_DEMANDES", nullable = false)
    private Integer nbDemandes = 0;

    /** Demandes dont le statut est final positif ce mois */
    @Builder.Default
    @Column(name = "NB_VALIDEES", nullable = false)
    private Integer nbValidees = 0;

    /** Demandes dont le statut est REFUSE ce mois */
    @Builder.Default
    @Column(name = "NB_REFUSEES", nullable = false)
    private Integer nbRefusees = 0;

    @Column(name = "DATE_MISE_A_JOUR")
    private LocalDateTime dateMiseAJour;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.dateMiseAJour = LocalDateTime.now();
    }

    /* ── Méthodes métier ── */

    /** Taux d'absentéisme du mois. Approximation : 22 jours ouvrables. */
    public double getTauxAbsenteisme() {
        if (nbJoursConge == null || nbJoursConge == 0.0) return 0.0;
        return Math.round((nbJoursConge * 100.0 / 22.0) * 10.0) / 10.0;
    }

    /** Taux de validation des demandes ce mois. */
    public double getTauxValidation() {
        if (nbDemandes == null || nbDemandes == 0) return 0.0;
        return Math.round((nbValidees * 100.0 / nbDemandes) * 10.0) / 10.0;
    }

    /** Appelé par StatsService.saveEvent() lors d'un congé VALIDE_RH. */
    public void ajouterCongeValide(double joursConge) {
        if (this.nbDemandes  == null) this.nbDemandes  = 0;
        if (this.nbValidees  == null) this.nbValidees  = 0;
        if (this.nbJoursConge == null) this.nbJoursConge = 0.0;
        this.nbDemandes++;
        this.nbValidees++;
        this.nbJoursConge += joursConge;
    }

    /** Appelé par StatsService.saveEvent() lors de tout statut REFUSE. */
    public void ajouterDemandeRefusee() {
        if (this.nbDemandes == null) this.nbDemandes = 0;
        if (this.nbRefusees == null) this.nbRefusees = 0;
        this.nbDemandes++;
        this.nbRefusees++;
    }
}