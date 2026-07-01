package com.gerai.notificationservice.enums;

import lombok.Getter;

/**
 * Enumération des types d'événements pouvant déclencher une notification dans Synapse.
 * <p>
 * {@code @Getter} (Lombok) : génère les accesseurs {@code getLabel()} et {@code getColor()}
 * utilisés par {@code EmailService} pour construire les emails HTML stylisés.
 * </p>
 * <p>
 * Chaque valeur associe un libellé lisible et un code couleur hexadécimal utilisé
 * à la fois dans les emails HTML et dans l'interface Angular pour différencier
 * visuellement les notifications par nature.
 * </p>
 *
 * @since 1.0
 */
@Getter
public enum TypeNotification {

    /** Notification de nouvelle demande soumise (bleu — action attendue du chef/RH). */
    NOUVELLE_DEMANDE("Nouvelle demande", "#3498db"),

    /** Notification d'approbation d'une demande (vert — résultat positif pour l'employé). */
    DEMANDE_APPROUVEE("Demande approuvée", "#2ecc71"),

    /** Notification de rejet d'une demande (rouge — résultat négatif pour l'employé). */
    DEMANDE_REJETEE("Demande rejetée", "#e74c3c"),

    /** Notification de nouveau message interne (jaune — communication directe). */
    NOUVEAU_MESSAGE("Nouveau message", "#f1c40f"),

    /** Notification de rappel (violet — action à réaliser avant échéance). */
    RAPPEL("Rappel", "#9b59b6"),

    /** Notification d'information générale (gris — information sans action urgente requise). */
    INFO("Information", "#95a5a6");

    /** Libellé lisible par l'humain affiché dans les emails et l'interface Angular. */
    private final String label;

    /** Code couleur hexadécimal utilisé pour styliser les emails HTML et les badges de notification. */
    private final String color;

    /**
     * Constructeur de l'énumération.
     *
     * @param label libellé lisible du type de notification
     * @param color code couleur hexadécimal associé au type
     */
    TypeNotification(String label, String color) {
        this.label = label;
        this.color = color;
    }

    /**
     * Convertit un statut métier reçu depuis Kafka (ex. : {@code EN_ATTENTE}, {@code VALIDEE_RH})
     * vers le {@link TypeNotification} sémantique correspondant.
     * Retourne {@link #INFO} si le statut est inconnu ou null.
     *
     * @param statut le statut brut reçu depuis le microservice producteur Kafka
     * @return le {@link TypeNotification} correspondant au statut, ou {@link #INFO} par défaut
     */
    public static TypeNotification fromStatut(String statut) {
        if (statut == null) return INFO;

        return switch (statut.toUpperCase()) {
            case "EN_ATTENTE", "VALIDEE_CHEF" -> NOUVELLE_DEMANDE;
            case "VALIDEE_RH" -> DEMANDE_APPROUVEE;
            case "REJETEE" -> DEMANDE_REJETEE;
            case "ANNULEE" -> INFO;
            default -> INFO;
        };
    }
}