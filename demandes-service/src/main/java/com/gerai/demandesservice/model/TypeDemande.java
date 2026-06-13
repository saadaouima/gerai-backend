package com.gerai.demandesservice.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Discriminant du type de demande RH.
 * Correspond aux 5 tables spécifiques de la base GERAI.
 *
 * Mapping :
 *   CONGE          → LEAVE_REQUESTS
 *   FORMATION      → TRAINING_REQUESTS
 *   PRET           → LOAN_REQUESTS
 *   DOCUMENT       → DOCUMENT_REQUESTS
 *   AUTORISATION   → AUTHORIZATION_REQUESTS
 *
 * CORRECTION : DOCUMENT_ADMINISTRATIF renommé en DOCUMENT
 * pour cohérence avec la valeur TYPE dans V_ALL_DEMANDES.
 */
public enum TypeDemande {

    CONGE,
    FORMATION,
    PRET,
    DOCUMENT,
    AUTORISATION;

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

    /** Serialize to Angular-compatible names */
    @JsonValue
    public String toValue() {
        return switch (this) {
            case DOCUMENT     -> "DOCUMENT_ADMINISTRATIF";
            case AUTORISATION -> "AUTRE";
            default           -> name();
        };
    }
}