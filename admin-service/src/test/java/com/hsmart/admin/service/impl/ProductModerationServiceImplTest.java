package com.hsmart.admin.service.impl;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hsmart.admin.application.dto.DetectionDTO;
import com.hsmart.admin.application.dto.ProductCreatedEvent;
import com.hsmart.admin.application.dto.SellerTrustResponseDTO;
import com.hsmart.admin.infrastructure.persistence.AdminNotificationRepository;
import com.hsmart.admin.service.ProductAdminClient;
import com.hsmart.admin.service.UserAdminClient;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductModerationServiceImplTest {

    @Mock
    private ProductAdminClient productAdminClient;

    @Mock
    private UserAdminClient userAdminClient;

    @Mock
    private AdminNotificationRepository adminNotificationRepository;

    @Test
    void shouldApproveProductWhenAiCategoryMatchesSelectedCategoryAndSellerIsTrusted() {
        ProductModerationServiceImpl service = new ProductModerationServiceImpl(
                productAdminClient,
                userAdminClient,
                adminNotificationRepository
        );
        given(userAdminClient.getSellerTrustProfile("seller-1")).willReturn(trustedSeller("seller-1"));

        service.moderateProduct(ProductCreatedEvent.builder()
                .id(10L)
                .title("May giat cu")
                .categoryName("May giat")
                .sellerId("seller-1")
                .aiMetadata(List.of(DetectionDTO.builder()
                        .label("washing machine")
                        .score(0.91)
                        .build()))
                .build());

        verify(productAdminClient).updateModerationStatus(10L, "APPROVED");
        verifyNoInteractions(adminNotificationRepository);
    }

    @Test
    void shouldMarkPendingReviewAndNotifyAdminWhenConfidenceIsLow() {
        ProductModerationServiceImpl service = new ProductModerationServiceImpl(
                productAdminClient,
                userAdminClient,
                adminNotificationRepository
        );
        given(userAdminClient.getSellerTrustProfile("seller-2")).willReturn(trustedSeller("seller-2"));

        service.moderateProduct(ProductCreatedEvent.builder()
                .id(11L)
                .title("Unknown item")
                .categoryName("Tu lanh")
                .sellerId("seller-2")
                .aiMetadata(List.of(DetectionDTO.builder()
                        .label("refrigerator")
                        .score(0.42)
                        .build()))
                .build());

        verify(productAdminClient).updateModerationStatus(11L, "PENDING_REVIEW");
        verify(adminNotificationRepository).save(argThat(notification ->
                notification.getProductId().equals(11L)
                        && notification.getType().equals("PRODUCT_PENDING_REVIEW")
                        && notification.getMessage().contains("AI confidence is below the moderation threshold")
        ));
    }

    @Test
    void shouldMarkPendingReviewAndNotifyAdminWhenSellerTrustIsLow() {
        ProductModerationServiceImpl service = new ProductModerationServiceImpl(
                productAdminClient,
                userAdminClient,
                adminNotificationRepository
        );
        given(userAdminClient.getSellerTrustProfile("seller-3")).willReturn(SellerTrustResponseDTO.builder()
                .sellerId("seller-3")
                .trustScore(BigDecimal.valueOf(4.3))
                .reviewCount(12L)
                .build());

        service.moderateProduct(ProductCreatedEvent.builder()
                .id(12L)
                .title("Good chair")
                .categoryName("Chair")
                .sellerId("seller-3")
                .aiMetadata(List.of(DetectionDTO.builder()
                        .label("chair")
                        .score(0.88)
                        .build()))
                .build());

        verify(productAdminClient).updateModerationStatus(12L, "PENDING_REVIEW");
        verify(adminNotificationRepository).save(argThat(notification ->
                notification.getProductId().equals(12L)
                        && notification.getType().equals("PRODUCT_PENDING_REVIEW")
                        && notification.getMessage().contains("Seller trust score or review count is below moderation thresholds")
        ));
    }

    @Test
    void shouldFallbackToPendingReviewWhenSellerTrustLookupFails() {
        ProductModerationServiceImpl service = new ProductModerationServiceImpl(
                productAdminClient,
                userAdminClient,
                adminNotificationRepository
        );
        given(userAdminClient.getSellerTrustProfile("seller-4")).willThrow(new RuntimeException("User-service timeout"));

        service.moderateProduct(ProductCreatedEvent.builder()
                .id(13L)
                .title("Reliable fan")
                .categoryName("Fan")
                .sellerId("seller-4")
                .aiMetadata(List.of(DetectionDTO.builder()
                        .label("fan")
                        .score(0.9)
                        .build()))
                .build());

        verify(productAdminClient).updateModerationStatus(13L, "PENDING_REVIEW");
        verify(adminNotificationRepository).save(argThat(notification ->
                notification.getProductId().equals(13L)
                        && notification.getMessage().contains("Moderation fallback triggered because a downstream service call failed")
        ));
    }

    private SellerTrustResponseDTO trustedSeller(String sellerId) {
        return SellerTrustResponseDTO.builder()
                .sellerId(sellerId)
                .trustScore(BigDecimal.valueOf(4.8))
                .reviewCount(6L)
                .build();
    }
}
