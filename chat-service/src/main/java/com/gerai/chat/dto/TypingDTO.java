package com.gerai.chat.dto;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class TypingDTO {
    private Long    conversationId;
    private boolean typing;
    private Long    destinataireEmployeeId;
}
