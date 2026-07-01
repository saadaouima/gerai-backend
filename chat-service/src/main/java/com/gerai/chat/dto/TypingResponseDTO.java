package com.gerai.chat.dto;

import lombok.*;

/**
 * DTO envoyé via WebSocket au destinataire pour l'informer qu'un autre utilisateur
 * est en train d'écrire dans leur conversation.
 * <p>
 * Diffusé sur la file personnelle {@code /user/{employeeId}/queue/typing}.
 *
 * @since 1.0
 */
@Data @AllArgsConstructor
public class TypingResponseDTO {

    /** ID Oracle de l'employé qui est en train d'écrire. */
    private Long    senderEmployeeId;

    /** Identifiant Oracle de la conversation concernée. */
    private Long    conversationId;

    /** {@code true} si l'expéditeur est en train de taper, {@code false} s'il a arrêté. */
    private boolean typing;
}