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
public class NotificationResponseDTO {
    private String id;
    private String userId;
    private String title;
    private String type;
    private String message;
    private Long productId;
    private Long orderId;
    private Long offerId;
    private String senderId;
    private boolean read;
    private long unreadCount;
    private Instant timestamp;
}
