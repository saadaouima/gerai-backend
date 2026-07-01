package com.gerai.chat.dto.ai;

/**
 * Enregistrement immuable représentant un message dans l'historique de conversation avec le chatbot IA.
 * <p>
 * Format compatible avec l'API OpenAI / Groq : chaque message possède un rôle
 * ({@code user} ou {@code assistant}) et un contenu textuel.
 * Utilisé dans {@link AiChatRequest#getHistory()} et transmis à l'API Groq via
 * {@link com.gerai.chat.service.AiChatService}.
 *
 * @param role    le rôle de l'auteur du message : {@code user} (employé) ou {@code assistant} (chatbot)
 * @param content le contenu textuel du message
 * @since 1.0
 */
public record AiMessage(String role, String content) {}
