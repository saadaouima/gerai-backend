package com.gerai.chat.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class CreateConversationDTO {
    /** ID Oracle de l'autre participant (conversation directe) */
    private Long   otherEmployeeId;
    /** Pour les groupes : liste de tous les participants */
    private List<Long> participantIds;
    /** Nom du groupe (optionnel pour DIRECT) */
    private String name;
    /** DIRECT | GROUPE (défaut : DIRECT) */
    private String type;
}