package com.hsmart.backend.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductStatsResponseDTO {
    private long totalSellingProducts;
    private long totalPendingReview;
    private long totalHidden;
    private long totalSold;
}
