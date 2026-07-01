package com.gerai.chat.dto;

import lombok.*;

/**
 * DTO envoyé par le client WebSocket pour signaler qu'un utilisateur est en train d'écrire.
 * <p>
 * Reçu sur la destination STOMP {@code /app/chat.typing}.
 * L'indicateur est retransmis au destinataire sous forme de {@link TypingResponseDTO}.
 *
 * @since 1.0
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class TypingDTO {

    /** Identifiant Oracle de la conversation dans laquelle l'utilisateur est en train d'écrire. */
    private Long    conversationId;

    /** {@code true} si l'utilisateur commence à taper, {@code false} s'il s'arrête. */
    private boolean typing;

    /** ID Oracle de l'employé destinataire auquel l'indicateur de frappe doit être envoyé. */
    private Long    destinataireEmployeeId;
}