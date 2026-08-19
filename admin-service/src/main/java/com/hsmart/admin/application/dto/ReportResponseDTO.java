package com.hsmart.admin.application.dto;

import com.hsmart.admin.domain.entities.ReportStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponseDTO {
    private Long id;
    private String reporterId;
    private Long productId;
    private String reason;
    private ReportStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private String processedBy;
    private String resolutionReason;
}
