package com.gerai.chat.dto;

import lombok.*;


@Data @NoArgsConstructor @AllArgsConstructor
public class EnvoiMessageDTO {
    private Long   conversationId;
    private String content;
    private String type;          // TEXTE | IMAGE | FICHIER (défaut : TEXTE)
    private String attachmentUrl;
    private Long   replyToId;
}