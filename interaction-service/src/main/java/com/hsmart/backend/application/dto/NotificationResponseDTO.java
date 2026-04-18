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
    private String type;
    private String message;
    private Long productId;
    private boolean read;
    private Instant timestamp;
}
