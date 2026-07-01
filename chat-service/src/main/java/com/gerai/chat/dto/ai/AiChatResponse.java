package com.gerai.chat.dto.ai;

/**
 * DTO de réponse du chatbot IA RH renvoyé au frontend.
 * <p>
 * Retourné en JSON par {@code POST /api/chat/ai/message}.
 * Utilise le pattern factory (méthodes statiques {@link #success} et {@link #error})
 * pour garantir la cohérence entre {@code success}, {@code reply} et {@code error}.
 *
 * @since 1.0
 */
public class AiChatResponse {

    /** Texte de la réponse générée par le chatbot (ou message d'erreur si {@code success = false}). */
    private String  reply;

    /** {@code true} si le chatbot a généré une réponse valide, {@code false} en cas d'erreur. */
    private boolean success;

    /** Description de l'erreur survenue ({@code null} si {@code success = true}). */
    private String  error;

    /** Constructeur privé — utiliser les méthodes factory {@link #success} et {@link #error}. */
    private AiChatResponse() {}

    /**
     * Crée une réponse de succès avec le texte généré par le chatbot.
     *
     * @param reply la réponse textuelle du chatbot
     * @return une instance {@link AiChatResponse} avec {@code success = true}
     */
    public static AiChatResponse success(String reply) {
        AiChatResponse r = new AiChatResponse();
        r.reply   = reply;
        r.success = true;
        return r;
    }

    /**
     * Crée une réponse d'erreur avec le message d'erreur.
     *
     * @param message la description de l'erreur survenue
     * @return une instance {@link AiChatResponse} avec {@code success = false}
     */
    public static AiChatResponse error(String message) {
        AiChatResponse r = new AiChatResponse();
        r.error   = message;
        r.success = false;
        r.reply   = message;
        return r;
    }

    /**
     * Retourne la réponse textuelle du chatbot.
     *
     * @return le texte de réponse (ou le message d'erreur si {@code success = false})
     */
    public String  getReply()   { return reply;   }

    /**
     * Indique si la requête au chatbot s'est terminée avec succès.
     *
     * @return {@code true} si le chatbot a répondu normalement
     */
    public boolean isSuccess()  { return success; }

    /**
     * Retourne le message d'erreur en cas d'échec.
     *
     * @return la description de l'erreur, ou {@code null} si succès
     */
    public String  getError()   { return error;   }
}
