package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entité JPA représentant un type de congé du référentiel RH.
 * <p>
 * Élément de référence géré via {@code /api/admin/referentiel/types-conge}.
 * Définit les types de congés disponibles (congé annuel, maladie, maternité, etc.)
 * avec leur durée légale, leur caractère payé et leur aspect visuel dans l'interface.
 * </p>
 * <p>
 * {@code @Entity} : classe persistée en base Oracle.<br>
 * {@code @Table(name = "REF_TYPES_CONGE")} : nom de la table Oracle.
 * </p>
 *
 * @since 1.0
 */
@Entity @Table(name = "REF_TYPES_CONGE")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TypeConge {
    /** Identifiant unique du type de congé (clé primaire générée). */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "TYPE_ID")
    private Long id;
    /** Code court du type de congé (ex. {@code CA} pour congé annuel). */
    @Column(name = "CODE")        private String  code;
    /** Libellé complet du type de congé (ex. "Congé annuel"). */
    @Column(name = "LIBELLE")     private String  libelle;
    /** Description détaillée du type de congé et de ses conditions d'utilisation. */
    @Column(name = "DESCRIPTION", length = 1000) private String description;
    /** Nombre de jours légaux alloués pour ce type de congé. */
    @Column(name = "NOMBRE_JOURS") private Integer nombreJours;
    /** Indique si ce type de congé est rémunéré ({@code true}) ou non ({@code false}). */
    @Column(name = "PAYE")        private Boolean paye;
    /** Indique si ce type de congé est actif et disponible pour les demandes. */
    @Column(name = "ACTIF")       private Boolean actif;
    /** Couleur hexadécimale associée à ce type dans l'interface (ex. {@code #4680FF}). */
    @Column(name = "COULEUR")     private String  couleur;
    /** Classe d'icône Tabler Icons associée à ce type (ex. {@code ti ti-calendar}). */
    @Column(name = "ICONE")       private String  icone;
}
