package com.hsmart.backend.domain.entities;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_messages")
public class ChatMessage {

    @Id
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
