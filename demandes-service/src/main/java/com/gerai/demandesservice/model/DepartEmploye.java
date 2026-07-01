package com.gerai.demandesservice.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entité JPA représentant un départ d'employé de l'entreprise.
 * Mappée sur la table Oracle {@code GERAI.DEPARTS_EMPLOYES}.
 * <p>
 * Un départ peut être de différentes natures : démission volontaire, licenciement,
 * départ à la retraite, fin de contrat CDD, mutation interne ou décès.
 * Le {@link com.gerai.demandesservice.scheduler.ContratExpiryScheduler} surveille
 * les fins de contrat à venir et alerte les managers concernés.
 *
 * @since 1.0
 */
@Entity @Table(name = "DEPARTS_EMPLOYES")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DepartEmploye {

    /** Identifiant technique du départ (DEPARTS_EMPLOYES.DEPART_ID). */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "DEPART_ID")
    private Long id;

    /** FK → EMPLOYEES.EMPLOYEE_ID — employé concerné par ce départ. */
    @Column(name = "EMPLOYE_ID")   private Long   employeId;

    /** Nom complet de l'employé (dénormalisé pour affichage sans jointure). */
    @Column(name = "EMPLOYE_NOM")  private String employeNom;

    /** Intitulé du poste de l'employé au moment du départ. */
    @Column(name = "EMPLOYE_POSTE") private String employePoste;

    /** URL de la photo de profil de l'employé. */
    @Column(name = "EMPLOYE_PHOTO") private String employePhoto;

    /** Nom du département de l'employé au moment du départ. */
    @Column(name = "EMPLOYE_DEPT") private String employeDept;

    /** Date effective du départ, stockée au format {@code DD/MM/YYYY} en base Oracle. */
    @Column(name = "DATE_DEPART")  private String dateDepart;

    /** Nature du départ : {@code DEMISSION} | {@code LICENCIEMENT} | {@code RETRAITE} | {@code FIN_CONTRAT} | {@code MUTATION} | {@code DECES}. */
    @Column(name = "TYPE_DEPART")  private String typeDepart;

    /** Justification ou motif détaillé du départ. */
    @Column(name = "RAISON", length = 2000) private String raison;

    /** Statut de traitement administratif du départ : {@code EN_COURS} | {@code VALIDE} | {@code ANNULE}. */
    @Column(name = "STATUT")       private String statut;

    /** Notes internes complémentaires sur le départ (procédures, remise de matériel, etc.). */
    @Column(name = "NOTES", length = 2000)  private String notes;
}
