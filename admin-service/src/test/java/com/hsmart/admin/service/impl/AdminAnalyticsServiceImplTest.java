package com.hsmart.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hsmart.admin.application.dto.OrderStatsResponseDTO;
import com.hsmart.admin.application.dto.OverviewStatsResponseDTO;
import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.application.dto.ProductStatsResponseDTO;
import com.hsmart.admin.application.dto.SystemAccountSummaryDTO;
import com.hsmart.admin.application.dto.SystemLedgerEntryDTO;
import com.hsmart.admin.application.dto.UserStatsResponseDTO;
import com.hsmart.admin.service.PaymentAdminClient;
import com.hsmart.admin.service.StatsClient;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsServiceImplTest {

    @Mock
    private StatsClient statsClient;

    @Mock
    private PaymentAdminClient paymentAdminClient;

    private AdminAnalyticsServiceImpl adminAnalyticsService;

    @BeforeEach
    void setUp() {
        adminAnalyticsService = new AdminAnalyticsServiceImpl(statsClient, paymentAdminClient);
    }

    @Test
    void getOverviewStatsShouldIncludePlatformFeeRevenue() {
        given(statsClient.getUserStats()).willReturn(userStats(120));
        given(statsClient.getProductStats()).willReturn(productStats(45));
        given(statsClient.getOrderStats()).willReturn(orderStats(BigDecimal.valueOf(900000)));
        given(paymentAdminClient.getSystemAccountSummary()).willReturn(summary(BigDecimal.valueOf(50000)));

        OverviewStatsResponseDTO response = adminAnalyticsService.getOverviewStats();

        assertThat(response.getTotalUsers()).isEqualTo(120);
        assertThat(response.getTotalSellingProducts()).isEqualTo(45);
        assertThat(response.getTotalCompletedRevenue()).isEqualByComparingTo("900000");
        assertThat(response.getPlatformFeeRevenue()).isEqualByComparingTo("50000");
    }

    @Test
    void getOverviewStatsShouldDegradePlatformFeeToZeroWhenPaymentServiceUnavailable() {
        given(statsClient.getUserStats()).willReturn(userStats(1));
        given(statsClient.getProductStats()).willReturn(productStats(1));
        given(statsClient.getOrderStats()).willReturn(orderStats(BigDecimal.TEN));
        given(paymentAdminClient.getSystemAccountSummary()).willThrow(new RuntimeException("payment-service down"));

        OverviewStatsResponseDTO response = adminAnalyticsService.getOverviewStats();

        assertThat(response.getPlatformFeeRevenue()).isEqualByComparingTo("0");
    }

    @Test
    void getSystemAccountShouldDelegateToPaymentClient() {
        given(paymentAdminClient.getSystemAccountSummary()).willReturn(summary(BigDecimal.valueOf(30000)));

        SystemAccountSummaryDTO response = adminAnalyticsService.getSystemAccount();

        assertThat(response.getTotalCredited()).isEqualByComparingTo("30000");
    }

    @Test
    void getSystemLedgerShouldDelegateToPaymentClient() {
        SystemLedgerEntryDTO entry = SystemLedgerEntryDTO.builder()
                .id(1L).entryType("PLATFORM_FEE_IN").direction("CREDIT")
                .amount(BigDecimal.valueOf(10000)).balanceAfter(BigDecimal.valueOf(10000))
                .referenceType("ORDER").referenceId(5L).build();
        given(paymentAdminClient.listLedger(0, 20)).willReturn(
                PageResponseDTO.<SystemLedgerEntryDTO>builder().content(List.of(entry)).totalElements(1).build());

        PageResponseDTO<SystemLedgerEntryDTO> response = adminAnalyticsService.getSystemLedger(0, 20);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getReferenceId()).isEqualTo(5L);
    }

    private UserStatsResponseDTO userStats(long total) {
        UserStatsResponseDTO dto = new UserStatsResponseDTO();
        dto.setTotalUsers(total);
        return dto;
    }

    private ProductStatsResponseDTO productStats(long total) {
        ProductStatsResponseDTO dto = new ProductStatsResponseDTO();
        dto.setTotalSellingProducts(total);
        return dto;
    }

    private OrderStatsResponseDTO orderStats(BigDecimal revenue) {
        OrderStatsResponseDTO dto = new OrderStatsResponseDTO();
        dto.setTotalCompletedRevenue(revenue);
        return dto;
    }

    private SystemAccountSummaryDTO summary(BigDecimal credited) {
        return SystemAccountSummaryDTO.builder()
                .balance(credited)
                .totalCredited(credited)
                .totalDebited(BigDecimal.ZERO)
                .entryCount(1)
                .build();
    }
}
