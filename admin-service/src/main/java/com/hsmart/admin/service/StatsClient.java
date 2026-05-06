package com.hsmart.admin.service;

import com.hsmart.admin.application.dto.OrderStatsResponseDTO;
import com.hsmart.admin.application.dto.ProductStatsResponseDTO;
import com.hsmart.admin.application.dto.UserStatsResponseDTO;

public interface StatsClient {
    UserStatsResponseDTO getUserStats();
    ProductStatsResponseDTO getProductStats();
    OrderStatsResponseDTO getOrderStats();
}
