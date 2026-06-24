package com.hsmart.backend.application.dto;

import com.hsmart.backend.domain.entities.ChatMessageType;
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
    private ChatMessageType messageType;
    private String senderId;
    private String receiverId;
    private Long productId;
    private String content;
    private String mediaUrl;
    private String mediaMimeType;
    private Long mediaSizeBytes;
    private String mediaOriginalFilename;
    private Instant timestamp;
}
