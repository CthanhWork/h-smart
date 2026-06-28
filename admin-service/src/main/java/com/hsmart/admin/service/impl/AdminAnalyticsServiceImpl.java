package com.hsmart.admin.service.impl;

import com.hsmart.admin.application.dto.OrderStatsResponseDTO;
import com.hsmart.admin.application.dto.OverviewStatsResponseDTO;
import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.application.dto.ProductStatsResponseDTO;
import com.hsmart.admin.application.dto.SystemAccountSummaryDTO;
import com.hsmart.admin.application.dto.SystemLedgerEntryDTO;
import com.hsmart.admin.application.dto.UserStatsResponseDTO;
import com.hsmart.admin.service.AdminAnalyticsService;
import com.hsmart.admin.service.PaymentAdminClient;
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
    private final PaymentAdminClient paymentAdminClient;

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
                .platformFeeRevenue(resolvePlatformFeeRevenue())
                .build();

        log.info("Fetched admin overview stats in {} ms", System.currentTimeMillis() - startedAt);
        return response;
    }

    @Override
    public SystemAccountSummaryDTO getSystemAccount() {
        return paymentAdminClient.getSystemAccountSummary();
    }

    @Override
    public PageResponseDTO<SystemLedgerEntryDTO> getSystemLedger(int page, int size) {
        return paymentAdminClient.listLedger(page, size);
    }

    /**
     * Platform fee revenue is a best-effort enrichment of the overview: a payment-service
     * outage degrades it to zero instead of failing the whole dashboard.
     */
    private BigDecimal resolvePlatformFeeRevenue() {
        try {
            BigDecimal credited = paymentAdminClient.getSystemAccountSummary().getTotalCredited();
            return credited != null ? credited : BigDecimal.ZERO;
        } catch (RuntimeException exception) {
            log.warn("Platform fee revenue unavailable for overview; defaulting to zero", exception);
            return BigDecimal.ZERO;
        }
    }
}
