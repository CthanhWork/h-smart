package com.hsmart.admin.service;

import com.hsmart.admin.application.dto.AdminNotificationResponseDTO;
import com.hsmart.admin.application.dto.ManualProductModerationRequestDTO;
import com.hsmart.admin.application.dto.NotificationCountResponseDTO;
import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.application.dto.OrderResponseDTO;
import com.hsmart.admin.application.dto.ReviewAdminSummaryDTO;
import com.hsmart.admin.application.dto.UserAdminSummaryDTO;
import org.springframework.data.domain.Pageable;

public interface AdminManagementService {
    void banUser(String userId);
    void unbanUser(String userId);
    void moderateProduct(Long productId, ManualProductModerationRequestDTO request, String adminUserId);
    PageResponseDTO<UserAdminSummaryDTO> listUsers(String search, Boolean isActive, Pageable pageable);
    UserAdminSummaryDTO getUserById(Long userId);
    PageResponseDTO<OrderResponseDTO> listAllOrders(String status, Pageable pageable);
    OrderResponseDTO adminCancelOrder(Long orderId);
    OrderResponseDTO adminApproveReturn(Long orderId);
    OrderResponseDTO adminRejectReturn(Long orderId, String reason);
    PageResponseDTO<ReviewAdminSummaryDTO> listAllReviews(Pageable pageable);
    ReviewAdminSummaryDTO hideReview(Long reviewId);
    ReviewAdminSummaryDTO restoreReview(Long reviewId);
    PageResponseDTO<AdminNotificationResponseDTO> listAdminNotifications(Boolean processed, Pageable pageable);
    NotificationCountResponseDTO getAdminUnreadNotificationCount();
}
