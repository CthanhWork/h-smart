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
public class ReviewAdminSummaryDTO {
    private Long id;
    private Long orderId;
    private String buyerId;
    private String sellerId;
    private Integer rating;
    private String comment;
    private boolean hidden;
    private LocalDateTime createdAt;
}
