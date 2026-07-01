package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entité JPA représentant un entretien de recrutement planifié.
 * <p>
 * Créé après la shortlist d'un candidat ; suit un workflow :
 * {@code PLANIFIE → EN_COURS → TERMINE} avec décision {@code RETENU/REJETE/EN_ATTENTE}.
 * Géré via {@code /api/admin/interviews}.
 * </p>
 * <p>
 * {@code @Entity} : classe persistée en base Oracle.<br>
 * {@code @Table(name = "INTERVIEWS")} : nom de la table Oracle.
 * </p>
 *
 * @since 1.0
 */
@Entity @Table(name = "INTERVIEWS")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Interview {

    /** Identifiant unique de l'entretien (clé primaire générée). */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "INTERVIEW_ID")
    private Long id;

    /** Identifiant du candidat convoqué à l'entretien. */
    @Column(name = "CANDIDAT_ID")     private Long   candidatId;
    /** Nom de famille du candidat (dénormalisé). */
    @Column(name = "CANDIDAT_NOM")    private String candidatNom;
    /** Prénom du candidat (dénormalisé). */
    @Column(name = "CANDIDAT_PRENOM") private String candidatPrenom;
    /** URL de la photo du candidat (dénormalisé). */
    @Column(name = "CANDIDAT_PHOTO")  private String candidatPhoto;

    /** Identifiant de l'offre d'emploi pour laquelle l'entretien est organisé. */
    @Column(name = "JOB_ID")      private Long   jobId;
    /** Titre du poste (dénormalisé pour l'affichage). */
    @Column(name = "JOB_TITRE")   private String jobTitre;
    /** Département concerné par le poste. */
    @Column(name = "DEPARTEMENT") private String departement;

    /** Type d'entretien : {@code TELEPHONIQUE}, {@code VISIO}, {@code PRESENTIEL}, {@code TECHNIQUE}, {@code RH}. */
    @Column(name = "TYPE")     private String type;
    /** Statut de l'entretien : {@code PLANIFIE}, {@code EN_COURS} ou {@code TERMINE}. */
    @Column(name = "STATUT")   private String statut;
    /** Décision post-entretien : {@code RETENU}, {@code REJETE} ou {@code EN_ATTENTE}. */
    @Column(name = "DECISION") private String decision;

    /** Date de l'entretien au format ISO (YYYY-MM-DD). */
    @Column(name = "DATE_ENTRETIEN") private String date;
    /** Heure de début de l'entretien (ex. "09:00"). */
    @Column(name = "HEURE_DEBUT")    private String heureDebut;
    /** Heure de fin de l'entretien (ex. "10:00"). */
    @Column(name = "HEURE_FIN")      private String heureFin;
    /** Lieu de l'entretien présentiel (adresse ou salle). */
    @Column(name = "LIEU")           private String lieu;
    /** Lien de visioconférence pour les entretiens à distance. */
    @Column(name = "LIEN_VISIO")     private String lienVisio;

    /** Nom complet de l'intervieweur. */
    @Column(name = "INTERVIEWEUR")        private String intervieweur;
    /** URL de la photo de l'intervieweur. */
    @Column(name = "INTERVIEWEUR_PHOTO")  private String intervieweurPhoto;

    /** Note globale attribuée au candidat après l'entretien (0-10). */
    @Column(name = "NOTE_GLOBALE")                  private Integer noteGlobale;
    /** Commentaires et observations de l'intervieweur. */
    @Column(name = "COMMENTAIRE", length = 2000)    private String  commentaire;
}
