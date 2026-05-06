package com.hsmart.order.application.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatsResponseDTO {
    private long completedOrderCount;
    private BigDecimal totalCompletedRevenue;
}
