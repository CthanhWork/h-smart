package com.hsmart.admin.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hsmart.admin.application.dto.ManualProductModerationRequestDTO;
import com.hsmart.admin.domain.entities.ManualProductModerationAction;
import com.hsmart.admin.infrastructure.persistence.AdminNotificationRepository;
import com.hsmart.admin.service.ProductAdminClient;
import com.hsmart.admin.service.UserAdminClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminManagementServiceImplTest {

    @Mock
    private UserAdminClient userAdminClient;

    @Mock
    private ProductAdminClient productAdminClient;

    @Mock
    private AdminNotificationRepository adminNotificationRepository;

    @Test
    void shouldBanUserThroughUserServiceClient() {
        AdminManagementServiceImpl service = service();

        service.banUser("buyer-1");

        verify(userAdminClient).updateUserActiveStatus("buyer-1", false);
    }

    @Test
    void shouldApproveProductAndResolvePendingReviewNotifications() {
        AdminManagementServiceImpl service = service();
        when(adminNotificationRepository.markProcessedByProductIdAndType(10L, "PRODUCT_PENDING_REVIEW"))
                .thenReturn(2);

        service.moderateProduct(10L, request(ManualProductModerationAction.APPROVE));

        verify(productAdminClient).updateModerationStatus(10L, "APPROVED");
        verify(adminNotificationRepository).markProcessedByProductIdAndType(10L, "PRODUCT_PENDING_REVIEW");
    }

    @Test
    void shouldRejectProductAndResolvePendingReviewNotifications() {
        AdminManagementServiceImpl service = service();

        service.moderateProduct(11L, request(ManualProductModerationAction.REJECT));

        verify(productAdminClient).updateModerationStatus(11L, "HIDDEN");
        verify(adminNotificationRepository).markProcessedByProductIdAndType(11L, "PRODUCT_PENDING_REVIEW");
    }

    private AdminManagementServiceImpl service() {
        return new AdminManagementServiceImpl(userAdminClient, productAdminClient, adminNotificationRepository);
    }

    private ManualProductModerationRequestDTO request(ManualProductModerationAction action) {
        return ManualProductModerationRequestDTO.builder()
                .action(action)
                .build();
    }
}
