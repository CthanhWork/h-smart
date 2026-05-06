package com.hsmart.admin.application.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverviewStatsResponseDTO {
    private long totalUsers;
    private long totalSellingProducts;
    private BigDecimal totalCompletedRevenue;
}
