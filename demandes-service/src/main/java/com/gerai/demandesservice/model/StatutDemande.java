package com.gerai.demandesservice.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Énumération des statuts unifiés des demandes RH exposée par l'API REST.
 * <p>
 * Chaque valeur est indépendante du type de demande et est traduite en valeur Oracle
 * spécifique par {@link #toOracleStatus(TypeDemande)} avant chaque écriture en base.
 * La conversion inverse est assurée par {@link #fromOracleStatus(String)}.
 * <p>
 * {@code @JsonCreator} / {@code @JsonValue} : assure la sérialisation/désérialisation
 * correcte par Jackson même si la valeur reçue du frontend ne correspond pas exactement
 * à un nom d'enum (gestion des alias et de la casse).
 *
 * @since 1.0
 */
public enum StatutDemande {

    /** Demande soumise, en attente de première validation. */
    EN_ATTENTE,

    /** Demande validée par le chef hiérarchique — en attente de validation RH. */
    VALIDEE_CHEF,

    /** Demande de crédit transmise au comité / Directeur Général pour étude. */
    EN_ETUDE_DG,

    /** Crédit approuvé par le comité — en attente de validation finale Direction RH. */
    VALIDEE_DG,

    /** Demande approuvée par le RH (étape finale pour la plupart des types). */
    VALIDEE_RH,

    /** Demande de crédit refusée par la commission (avis DEFAVORABLE). */
    REJETEE_COMMISSION,

    /** Demande rejetée à une quelconque étape du workflow. */
    REJETEE,

    /** Demande annulée par l'employé ou le gestionnaire RH. */
    ANNULEE,

    /** Congé longue maladie en attente de la décision du comité médical. */
    EN_ETUDE_MEDICALE;

    /**
     * Désérialise un statut depuis une chaîne JSON (insensible à la casse).
     * Retourne {@code EN_ATTENTE} en cas de valeur nulle ou non reconnue.
     *
     * @param value valeur textuelle du statut reçue du frontend Angular
     * @return l'enum correspondant, ou {@code EN_ATTENTE} par défaut
     */
    @JsonCreator
    public static StatutDemande from(String value) {
        if (value == null) return EN_ATTENTE;
        try {
            return StatutDemande.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return EN_ATTENTE;
        }
    }

    /**
     * Sérialise le statut en chaîne JSON — retourne le nom de l'enum.
     *
     * @return nom de l'enum (ex : {@code VALIDEE_CHEF})
     */
    @JsonValue
    public String toValue() {
        return name();
    }

    /**
     * Traduit ce statut API en valeur Oracle adaptée au type de demande donné.
     * Appelé par {@link com.gerai.demandesservice.service.DemandeService} avant
     * chaque UPDATE sur la table cible.
     *
     * @param type type de la demande (CONGE, FORMATION, PRET, DOCUMENT, AUTORISATION)
     * @return la valeur chaîne correspondante en base Oracle (ex : {@code VALIDE_CHEF})
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
            case EN_ETUDE_DG  -> "EN_ETUDE_DG"; // Crédit transmis au comité
            case VALIDEE_DG   -> "VALIDEE_DG";  // Comité approuvé, en attente validation RH finale
            case VALIDEE_RH   -> switch (type) {
                case CONGE        -> "VALIDE_RH";
                case FORMATION    -> "APPROUVE_RH";
                case PRET         -> "APPROUVE";
                case DOCUMENT     -> "LIVRE";
                case AUTORISATION -> "APPROUVE";
            };
            case REJETEE_COMMISSION -> "REFUSE_COMMISSION";
            case REJETEE           -> "REFUSE";
            case ANNULEE           -> "ANNULE";
            case EN_ETUDE_MEDICALE -> "EN_ETUDE_MEDICALE";
        };
    }

    /**
     * Convertit une valeur Oracle brute en statut API unifié.
     * <p>
     * Note : certaines valeurs Oracle (ex : {@code APPROUVE}) peuvent correspondre à
     * plusieurs statuts API selon le type ; le service force le bon statut au moment
     * de la validation pour lever cette ambiguïté.
     *
     * @param oracleStatus valeur brute du champ STATUS en base Oracle
     * @return le statut API correspondant, ou {@code EN_ATTENTE} si la valeur est inconnue
     */
    public static StatutDemande fromOracleStatus(String oracleStatus) {
        if (oracleStatus == null) return EN_ATTENTE;

        return switch (oracleStatus.toUpperCase()) {
            case "EN_ATTENTE" ->
                    EN_ATTENTE;

            case "VALIDE_CHEF", "APPROUVE_CHEF", "EN_ETUDE", "EN_COURS" ->
                    VALIDEE_CHEF;

            case "EN_ETUDE_DG" ->
                    EN_ETUDE_DG;

            case "VALIDEE_DG" ->
                    VALIDEE_DG;

            case "VALIDE_RH", "APPROUVE_RH", "APPROUVE", "LIVRE", "PRET", "TRAITE" ->
                    VALIDEE_RH;

            case "REFUSE_COMMISSION" ->
                    REJETEE_COMMISSION;

            case "REFUSE", "REJETE" ->
                    REJETEE;

            case "ANNULE", "REMBOURSE" ->
                    ANNULEE;

            case "EN_ETUDE_MEDICALE" ->
                    EN_ETUDE_MEDICALE;

            default ->
                    EN_ATTENTE;
        };
    }
}