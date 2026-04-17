package com.gerai.demandesservice.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Discriminant du type de demande RH.
 * Correspond aux 5 tables spécifiques de la base GERAI_USER.
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
        // Rétro-compatibilité avec l'ancien nom
        if ("DOCUMENT_ADMINISTRATIF".equalsIgnoreCase(value)) return DOCUMENT;
        return TypeDemande.valueOf(value.toUpperCase());
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}