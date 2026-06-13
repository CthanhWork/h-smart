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
public class ChatConversationSummaryDTO {
    private String participantId;
    private Long productId;
    private String lastMessage;
    private Instant updatedAt;
}
