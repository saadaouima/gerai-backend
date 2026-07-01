package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Entité JPA représentant un candidat ayant postulé à une offre d'emploi.
 * <p>
 * Créée lors d'une candidature publique ({@code POST /api/public/apply}) et gérée
 * par l'équipe RH via les endpoints admin. Suit un workflow de statuts :
 * {@code NOUVEAU → EN_REVUE → SHORTLISTE → INTERVIEWE → OFFRE_ENVOYEE → EMBAUCHE/REJETE}.
 * </p>
 * <p>
 * {@code @Entity} : classe persistée en base Oracle.<br>
 * {@code @Table(name = "CANDIDATES")} : nom de la table Oracle.
 * </p>
 *
 * @since 1.0
 */
@Entity @Table(name = "CANDIDATES")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Candidate {

    /** Identifiant unique du candidat (clé primaire générée). */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "CAND_ID")
    private Long id;

    /** Nom de famille du candidat. */
    @Column(name = "NOM")         private String nom;
    /** Prénom du candidat. */
    @Column(name = "PRENOM")      private String prenom;
    /** Adresse email du candidat (clé de déduplication avec {@code jobId}). */
    @Column(name = "EMAIL")       private String email;
    /** Numéro de téléphone du candidat. */
    @Column(name = "TELEPHONE")   private String telephone;
    /** URL de la photo de profil du candidat. */
    @Column(name = "PHOTO")       private String photo;

    /** Identifiant de l'offre d'emploi pour laquelle le candidat postule. */
    @Column(name = "JOB_ID")      private Long   jobId;
    /** Titre de l'offre d'emploi (dénormalisé pour l'affichage). */
    @Column(name = "JOB_TITRE")   private String jobTitre;
    /** Département concerné par l'offre d'emploi. */
    @Column(name = "DEPARTEMENT") private String departement;

    /** Statut courant dans le pipeline de recrutement. */
    @Column(name = "STATUT")           private String    statut;
    /** Date à laquelle le candidat a soumis sa candidature. */
    @Column(name = "DATE_POSTULATION") private LocalDate datePostulation;
    /** Années d'expérience professionnelle déclarées par le candidat. */
    @Column(name = "EXPERIENCE")       private Integer   experience;

    /**
     * Liste des compétences du candidat, stockée en CSV Oracle via {@link StringListConverter}.
     */
    @Column(name = "COMPETENCES", length = 1000)
    @Convert(converter = com.gerai.projetsservice.model.StringListConverter.class)
    private List<String> competences;

    /** Indique si le candidat a été sélectionné pour la shortlist RH. */
    @Column(name = "SHORTLISTE")   private Boolean shortliste = false;
    /** URL d'accès au CV téléversé du candidat. */
    @Column(name = "CV_URL")       private String  cvUrl;
    /** URL du profil LinkedIn du candidat. */
    @Column(name = "LINKEDIN")     private String  linkedin;
    /** Ville ou région de résidence du candidat. */
    @Column(name = "LOCALISATION") private String  localisation;

    /** Note textuelle du recruteur sur le candidat. */
    @Column(name = "NOTE_RECRUTEUR", length = 2000) private String  noteRecruteur;
    /** Score IA (0-100) calculé par {@link com.gerai.projetsservice.service.CvScoringService}. */
    @Column(name = "SCORE")                         private Integer score;
    /** Score de présélection (0-100) calculé à partir des réponses aux questions de screening. */
    @Column(name = "SCREENING_SCORE")               private Integer screeningScore;
}
