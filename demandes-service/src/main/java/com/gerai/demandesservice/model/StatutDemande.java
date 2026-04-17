package com.gerai.demandesservice.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Statut unifié du point de vue de l'API REST.
 * Traduit en valeurs Oracle spécifiques par DemandeService.toOracleStatus().
 */
public enum StatutDemande {

    EN_ATTENTE,
    VALIDEE_CHEF,
    VALIDEE_RH,
    REJETEE,
    ANNULEE;

    @JsonCreator
    public static StatutDemande from(String value) {
        if (value == null) return EN_ATTENTE;
        try {
            return StatutDemande.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return EN_ATTENTE;
        }
    }

    @JsonValue
    public String toValue() {
        return name();
    }

    /**
     * Convertit le statut API en valeur Oracle pour un type donné.
     * Appelé par DemandeService avant chaque UPDATE sur la table cible.
     */
    public String toOracleStatus(TypeDemande type) {
        return switch (this) {
            case EN_ATTENTE   -> "EN_ATTENTE";
            case VALIDEE_CHEF -> switch (type) {
                case CONGE        -> "VALIDE_CHEF";
                case FORMATION    -> "APPROUVE_CHEF";
                case PRET         -> "EN_ETUDE";
                case DOCUMENT     -> "EN_COURS";
                case AUTORISATION -> "APPROUVE"; // Flux direct
            };
            case VALIDEE_RH   -> switch (type) {
                case CONGE        -> "VALIDE_RH";
                case FORMATION    -> "APPROUVE_RH";
                case PRET         -> "APPROUVE";
                case DOCUMENT     -> "LIVRE";
                case AUTORISATION -> "APPROUVE";
            };
            case REJETEE -> "REFUSE";
            case ANNULEE -> "ANNULE";
        };
    }

    /**
     * Conversion inverse : valeur Oracle → enum API.
     * Note : Pour lever l'ambiguïté sur "APPROUVE", cette méthode reste générique,
     * mais le Service forcera le bon statut API lors de la validation.
     */
    public static StatutDemande fromOracleStatus(String oracleStatus) {
        if (oracleStatus == null) return EN_ATTENTE;

        return switch (oracleStatus.toUpperCase()) {
            case "EN_ATTENTE" ->
                    EN_ATTENTE;

            case "VALIDE_CHEF", "APPROUVE_CHEF", "EN_ETUDE", "EN_COURS" ->
                    VALIDEE_CHEF;

            case "VALIDE_RH", "APPROUVE_RH", "APPROUVE", "LIVRE", "PRET" ->
                    VALIDEE_RH;

            case "REFUSE", "REJETE" ->
                    REJETEE;

            case "ANNULE", "REMBOURSE" ->
                    ANNULEE;

            default ->
                    EN_ATTENTE;
        };
    }
}