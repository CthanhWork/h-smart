package com.hsmart.admin.service.impl;

import com.hsmart.admin.application.dto.OrderStatsResponseDTO;
import com.hsmart.admin.application.dto.OverviewStatsResponseDTO;
import com.hsmart.admin.application.dto.ProductStatsResponseDTO;
import com.hsmart.admin.application.dto.UserStatsResponseDTO;
import com.hsmart.admin.service.AdminAnalyticsService;
import com.hsmart.admin.service.StatsClient;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAnalyticsServiceImpl implements AdminAnalyticsService {

    private final StatsClient statsClient;

    @Override
    public OverviewStatsResponseDTO getOverviewStats() {
        long startedAt = System.currentTimeMillis();
        UserStatsResponseDTO userStats = statsClient.getUserStats();
        ProductStatsResponseDTO productStats = statsClient.getProductStats();
        OrderStatsResponseDTO orderStats = statsClient.getOrderStats();

        OverviewStatsResponseDTO response = OverviewStatsResponseDTO.builder()
                .totalUsers(userStats.getTotalUsers())
                .totalSellingProducts(productStats.getTotalSellingProducts())
                .totalCompletedRevenue(orderStats.getTotalCompletedRevenue() != null
                        ? orderStats.getTotalCompletedRevenue()
                        : BigDecimal.ZERO)
                .build();

        log.info("Fetched admin overview stats in {} ms", System.currentTimeMillis() - startedAt);
        return response;
    }
}
