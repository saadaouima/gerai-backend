package com.gerai.chat.dto.ai;

import java.util.List;

/**
 * DTO de requête envoyée au chatbot IA RH.
 * <p>
 * Reçu en corps JSON sur {@code POST /api/chat/ai/message}.
 * Contient le message courant de l'utilisateur et l'historique des échanges précédents
 * (limité aux 6 derniers tours par {@link com.gerai.chat.service.AiChatService}).
 *
 * @since 1.0
 */
public class AiChatRequest {

    /** Message textuel courant de l'utilisateur à envoyer au chatbot. */
    private String message;

    /** Historique de la conversation (rôles {@code user} et {@code assistant}) pour le contexte. */
    private List<AiMessage> history;

    /** Constructeur par défaut requis pour la désérialisation JSON. */
    public AiChatRequest() {}

    /**
     * Retourne le message courant de l'utilisateur.
     *
     * @return le message textuel envoyé au chatbot
     */
    public String getMessage() { return message; }

    /**
     * Définit le message courant de l'utilisateur.
     *
     * @param message le message textuel à envoyer
     */
    public void setMessage(String message) { this.message = message; }

    /**
     * Retourne l'historique de conversation pour le contexte du chatbot.
     *
     * @return la liste des messages précédents, ou {@code null} si c'est le début
     */
    public List<AiMessage> getHistory() { return history; }

    /**
     * Définit l'historique de conversation.
     *
     * @param history la liste des messages précédents (rôles {@code user}/{@code assistant})
     */
    public void setHistory(List<AiMessage> history) { this.history = history; }
}
