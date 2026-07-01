package com.gerai.demandesservice.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Énumération des types de demandes RH gérées par le demandes-service.
 * Chaque valeur correspond à une table Oracle distincte.
 * <p>
 * Mapping table Oracle :
 * <ul>
 *   <li>{@code CONGE}        → {@code GERAI.LEAVE_REQUESTS}</li>
 *   <li>{@code FORMATION}    → {@code GERAI.TRAINING_REQUESTS}</li>
 *   <li>{@code PRET}         → {@code GERAI.LOAN_REQUESTS}</li>
 *   <li>{@code DOCUMENT}     → {@code GERAI.DOCUMENT_REQUESTS}</li>
 *   <li>{@code AUTORISATION} → {@code GERAI.AUTHORIZATION_REQUESTS}</li>
 * </ul>
 * {@code @JsonCreator} gère les alias du frontend Angular
 * (ex : {@code DOCUMENT_ADMINISTRATIF} → {@code DOCUMENT}, {@code CREDIT} → {@code PRET}).
 *
 * @since 1.0
 */
public enum TypeDemande {

    /** Demande de congé — mappée sur LEAVE_REQUESTS. */
    CONGE,

    /** Demande de formation professionnelle — mappée sur TRAINING_REQUESTS. */
    FORMATION,

    /** Demande de prêt/crédit employé — mappée sur LOAN_REQUESTS. */
    PRET,

    /** Demande de document administratif — mappée sur DOCUMENT_REQUESTS. */
    DOCUMENT,

    /** Demande d'autorisation d'absence — mappée sur AUTHORIZATION_REQUESTS. */
    AUTORISATION;

    /**
     * Désérialise un type depuis une chaîne JSON (insensible à la casse, gestion des alias).
     *
     * @param value valeur textuelle reçue du frontend Angular
     * @return l'enum correspondant
     * @throws IllegalArgumentException si la valeur est nulle ou inconnue
     */
    @JsonCreator
    public static TypeDemande from(String value) {
        if (value == null) throw new IllegalArgumentException("TypeDemande ne peut pas être null");
        return switch (value.toUpperCase()) {
            case "DOCUMENT_ADMINISTRATIF"                             -> DOCUMENT;
            case "AUTRE"                                              -> AUTORISATION;
            case "CREDIT"                                             -> PRET;
            case "MALADIE", "ANNUEL", "RTT", "FAMILIAL", "HAJJ",
                 "MATERNITE", "POSTNATAL", "ALLAITEMENT", "SANS_SOLDE",
                 "LONGUE_MALADIE", "NAISSANCE_PERE",
                 "CREATION_ENTREPRISE", "OBLIGATIONS_LEGALES"         -> CONGE;
            default -> TypeDemande.valueOf(value.toUpperCase());
        };
    }

    /**
     * Sérialise le type en nom compatible avec le frontend Angular.
     * {@code DOCUMENT} → {@code DOCUMENT_ADMINISTRATIF}, {@code AUTORISATION} → {@code AUTRE}.
     *
     * @return libellé textuel compatible avec le composant Angular
     */
    @JsonValue
    public String toValue() {
        return switch (this) {
            case DOCUMENT     -> "DOCUMENT_ADMINISTRATIF";
            case AUTORISATION -> "AUTRE";
            default           -> name();
        };
    }
}