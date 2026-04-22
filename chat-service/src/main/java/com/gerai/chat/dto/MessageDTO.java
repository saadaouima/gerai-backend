package com.gerai.chat.dto;

import lombok.*;
import java.time.LocalDateTime;


@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class MessageDTO {
    private Long          messageId;
    private Long          conversationId;
    private Long          senderId;
    private String        senderNom;
    private String        content;
    private String        type;          // TEXTE | IMAGE | FICHIER | SYSTEME
    private String        attachmentUrl;
    private Long          replyToId;
    private boolean       isDeleted;
    private LocalDateTime sentAt;
    private LocalDateTime editedAt;
    private boolean       luParMoi;      // true si l'utilisateur courant a lu
}