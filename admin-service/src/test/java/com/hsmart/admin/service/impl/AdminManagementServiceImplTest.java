package com.hsmart.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hsmart.admin.application.dto.AdminNotificationResponseDTO;
import com.hsmart.admin.application.dto.ManualProductModerationRequestDTO;
import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.domain.entities.ManualProductModerationAction;
import com.hsmart.admin.domain.entities.AdminNotification;
import com.hsmart.admin.infrastructure.persistence.AdminNotificationRepository;
import java.time.LocalDateTime;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import com.hsmart.admin.service.OrderAdminClient;
import com.hsmart.admin.service.ProductAdminClient;
import com.hsmart.admin.service.ReviewAdminClient;
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

    @Mock
    private OrderAdminClient orderAdminClient;

    @Mock
    private ReviewAdminClient reviewAdminClient;

    @Test
    void shouldBanUserThroughUserServiceClient() {
        AdminManagementServiceImpl service = service();

        service.banUser("buyer-1");

        verify(userAdminClient).updateUserActiveStatus("buyer-1", false);
    }

    @Test
    void shouldUnbanUserThroughUserServiceClient() {
        AdminManagementServiceImpl service = service();

        service.unbanUser("buyer-1");

        verify(userAdminClient).updateUserActiveStatus("buyer-1", true);
    }

    @Test
    void shouldApproveProductAndResolvePendingReviewNotifications() {
        AdminManagementServiceImpl service = service();
        when(adminNotificationRepository.markProcessedByProductIdAndType(
                org.mockito.Mockito.eq(10L),
                org.mockito.Mockito.eq("PRODUCT_PENDING_REVIEW"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.Mockito.eq("admin-1"),
                org.mockito.Mockito.eq("Ảnh đã khớp danh mục")))
                .thenReturn(2);

        service.moderateProduct(10L, request(ManualProductModerationAction.APPROVE, "Ảnh đã khớp danh mục"), "admin-1");

        verify(productAdminClient).updateModerationStatus(10L, "APPROVED");
        verify(adminNotificationRepository).markProcessedByProductIdAndType(
                org.mockito.Mockito.eq(10L),
                org.mockito.Mockito.eq("PRODUCT_PENDING_REVIEW"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.Mockito.eq("admin-1"),
                org.mockito.Mockito.eq("Ảnh đã khớp danh mục")
        );
    }

    @Test
    void shouldRejectProductAndResolvePendingReviewNotifications() {
        AdminManagementServiceImpl service = service();

        service.moderateProduct(11L, request(ManualProductModerationAction.REJECT, "Sai danh mục"), "admin-2");

        verify(productAdminClient).updateModerationStatus(11L, "HIDDEN");
        verify(adminNotificationRepository).markProcessedByProductIdAndType(
                org.mockito.Mockito.eq(11L),
                org.mockito.Mockito.eq("PRODUCT_PENDING_REVIEW"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.Mockito.eq("admin-2"),
                org.mockito.Mockito.eq("Sai danh mục")
        );
    }

    @Test
    void shouldListAdminNotifications() {
        AdminManagementServiceImpl service = service();
        AdminNotification notification = AdminNotification.builder()
                .id(5L)
                .productId(77L)
                .title("Máy giặt cũ")
                .type("PRODUCT_PENDING_REVIEW")
                .message("Product 77 requires admin review.")
                .createdAt(LocalDateTime.now())
                .processed(false)
                .build();
        when(adminNotificationRepository.findAllByProcessed(false, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(java.util.List.of(notification), PageRequest.of(0, 10), 1));

        PageResponseDTO<AdminNotificationResponseDTO> response =
                service.listAdminNotifications(false, PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getProductId()).isEqualTo(77L);
    }

    @Test
    void shouldReturnUnreadAdminNotificationCount() {
        AdminManagementServiceImpl service = service();
        when(adminNotificationRepository.countByProcessedFalse()).thenReturn(4L);

        var response = service.getAdminUnreadNotificationCount();

        assertThat(response.getUnreadCount()).isEqualTo(4L);
    }

    private AdminManagementServiceImpl service() {
        return new AdminManagementServiceImpl(userAdminClient, productAdminClient, adminNotificationRepository, orderAdminClient, reviewAdminClient);
    }

    private ManualProductModerationRequestDTO request(ManualProductModerationAction action, String reason) {
        return ManualProductModerationRequestDTO.builder()
                .action(action)
                .reason(reason)
                .build();
    }
}
