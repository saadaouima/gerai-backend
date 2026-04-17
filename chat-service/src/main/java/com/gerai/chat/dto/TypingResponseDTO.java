package com.gerai.chat.dto;

import lombok.*;

@Data @AllArgsConstructor
public class TypingResponseDTO {
    private Long    senderEmployeeId;
    private Long    conversationId;
    private boolean typing;
}