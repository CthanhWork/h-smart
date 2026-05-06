package com.hsmart.admin.application.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class OrderStatsResponseDTO {
    private long completedOrderCount;
    private BigDecimal totalCompletedRevenue;
}
