package com.hsmart.backend.application.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponseDTO {
    private String id;
    private String senderId;
    private String receiverId;
    private Long productId;
    private String content;
    private Instant timestamp;
}
