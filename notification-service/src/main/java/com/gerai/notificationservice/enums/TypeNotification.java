package com.gerai.notificationservice.enums;

import lombok.Getter;

/**
 * Types d'événements déclenchant une notification.
 * Utilisé par l'EmailService pour le style (couleurs) et par le Frontend.
 */
@Getter
public enum TypeNotification {

    NOUVELLE_DEMANDE("Nouvelle demande", "#3498db"),    // Bleu (Info/Action)
    DEMANDE_APPROUVEE("Demande approuvée", "#2ecc71"), // Vert (Succès)
    DEMANDE_REJETEE("Demande rejetée", "#e74c3c"),     // Rouge (Alerte)
    NOUVEAU_MESSAGE("Nouveau message", "#f1c40f"),     // Jaune
    RAPPEL("Rappel", "#9b59b6"),                      // Violet
    INFO("Information", "#95a5a6");                   // Gris

    private final String label;
    private final String color;

    TypeNotification(String label, String color) {
        this.label = label;
        this.color = color;
    }

    /**
     * Méthode utilitaire pour mapper le statut reçu de Kafka
     * vers un TypeNotification interne.
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