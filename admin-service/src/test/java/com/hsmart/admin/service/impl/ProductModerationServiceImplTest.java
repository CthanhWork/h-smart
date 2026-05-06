package com.hsmart.admin.service.impl;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hsmart.admin.application.dto.DetectionDTO;
import com.hsmart.admin.application.dto.ProductCreatedEvent;
import com.hsmart.admin.infrastructure.persistence.AdminNotificationRepository;
import com.hsmart.admin.service.ProductAdminClient;
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
    private AdminNotificationRepository adminNotificationRepository;

    @Test
    void shouldApproveProductWhenAiCategoryMatchesSelectedCategory() {
        ProductModerationServiceImpl service = new ProductModerationServiceImpl(
                productAdminClient,
                adminNotificationRepository
        );

        service.moderateProduct(ProductCreatedEvent.builder()
                .id(10L)
                .title("May giat cu")
                .categoryName("May giat")
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
                adminNotificationRepository
        );

        service.moderateProduct(ProductCreatedEvent.builder()
                .id(11L)
                .title("Unknown item")
                .categoryName("Tu lanh")
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
}
