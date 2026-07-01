package com.gerai.chat.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de création d'une conversation (directe ou groupe).
 * <p>
 * Reçu dans le corps des requêtes POST sur {@code /api/chat/conversations}
 * et {@code /api/chat/conversations/groupe}.
 *
 * @since 1.0
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class CreateConversationDTO {

    /** ID Oracle de l'autre participant pour une conversation directe. */
    private Long   otherEmployeeId;

    /** Liste des IDs Oracle de tous les participants pour un groupe. */
    private List<Long> participantIds;

    /** Nom du groupe (optionnel pour les conversations directes). */
    private String name;

    /** Type de conversation : {@code DIRECT} ou {@code GROUPE} (défaut : {@code DIRECT}). */
    private String type;
}