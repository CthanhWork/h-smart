package com.hsmart.admin.application.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminNotificationResponseDTO {
    private Long id;
    private Long productId;
    private Long reportId;
    private String title;
    private String type;
    private String message;
    private LocalDateTime createdAt;
    private boolean processed;
    private LocalDateTime processedAt;
    private String processedBy;
    private String resolutionReason;
}
