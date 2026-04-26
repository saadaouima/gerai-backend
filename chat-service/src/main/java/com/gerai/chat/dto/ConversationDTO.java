package com.gerai.chat.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

/* ═══════════════════════════════════════════════════════
   ConversationDTO
   ═══════════════════════════════════════════════════════ */

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ConversationDTO {

    private Long   conversationId;
    private String type;           // DIRECT | GROUPE | ANNONCE
    private String name;

    /** Participants avec leurs noms (calculés par JOIN EMPLOYEES) */
    private List<ParticipantDTO> participants;

    /** Dernier message pour l'aperçu dans la liste */
    private String          dernierMessage;
    private LocalDateTime   lastMessageAt;

    /** Nombre de messages non lus par l'utilisateur courant */
    private int nombreNonLus;

    /** ID Oracle de l'employé courant (pour savoir qui parle) */
    private Long currentEmployeeId;
}