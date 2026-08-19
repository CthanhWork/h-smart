package com.hsmart.admin.service.impl;

import com.hsmart.admin.application.dto.AdminNotificationResponseDTO;
import com.hsmart.admin.application.dto.ManualProductModerationRequestDTO;
import com.hsmart.admin.application.dto.NotificationCountResponseDTO;
import com.hsmart.admin.application.dto.OrderResponseDTO;
import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.application.dto.ReviewAdminSummaryDTO;
import com.hsmart.admin.application.dto.UserAdminSummaryDTO;
import com.hsmart.admin.application.exceptions.MissingUserContextException;
import com.hsmart.admin.domain.entities.ManualProductModerationAction;
import com.hsmart.admin.domain.entities.AdminNotification;
import com.hsmart.admin.infrastructure.persistence.AdminNotificationRepository;
import java.time.LocalDateTime;
import com.hsmart.admin.service.AdminManagementService;
import com.hsmart.admin.service.OrderAdminClient;
import com.hsmart.admin.service.ProductAdminClient;
import com.hsmart.admin.service.ReviewAdminClient;
import com.hsmart.admin.service.UserAdminClient;
import org.springframework.data.domain.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminManagementServiceImpl implements AdminManagementService {

    private static final String APPROVED_STATUS = "APPROVED";
    private static final String HIDDEN_STATUS = "HIDDEN";
    private static final String PENDING_REVIEW_NOTIFICATION_TYPE = "PRODUCT_PENDING_REVIEW";

    private final UserAdminClient userAdminClient;
    private final ProductAdminClient productAdminClient;
    private final AdminNotificationRepository adminNotificationRepository;
    private final OrderAdminClient orderAdminClient;
    private final ReviewAdminClient reviewAdminClient;

    @Override
    public void banUser(String userId) {
        userAdminClient.updateUserActiveStatus(userId, false);
        log.info("Banned user {}", userId);
    }

    @Override
    public void unbanUser(String userId) {
        userAdminClient.updateUserActiveStatus(userId, true);
        log.info("Unbanned user {}", userId);
    }

    @Override
    public void moderateProduct(Long productId, ManualProductModerationRequestDTO request, String adminUserId) {
        String resolvedAdminUserId = requireAdminUserId(adminUserId);
        ManualProductModerationAction action = request.getAction();
        String productStatus = action == ManualProductModerationAction.APPROVE ? APPROVED_STATUS : HIDDEN_STATUS;
        productAdminClient.updateModerationStatus(productId, productStatus);
        String resolutionReason = normalizeReason(request.getReason());
        int processedNotifications = adminNotificationRepository.markProcessedByProductIdAndType(
                productId,
                PENDING_REVIEW_NOTIFICATION_TYPE,
                LocalDateTime.now(),
                resolvedAdminUserId,
                resolutionReason
        );
        log.info(
                "Completed manual moderation for product {} with action {} and resolved {} admin notifications",
                productId,
                action,
                processedNotifications
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<UserAdminSummaryDTO> listUsers(String search, Boolean isActive, Pageable pageable) {
        return userAdminClient.listUsers(search, isActive, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public UserAdminSummaryDTO getUserById(Long userId) {
        return userAdminClient.getUserById(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<OrderResponseDTO> listAllOrders(String status, Pageable pageable) {
        return orderAdminClient.listAllOrders(status, pageable);
    }

    @Override
    public OrderResponseDTO adminCancelOrder(Long orderId) {
        OrderResponseDTO cancelled = orderAdminClient.adminCancelOrder(orderId);
        log.info("Admin cancelled order {}", orderId);
        return cancelled;
    }

    @Override
    public OrderResponseDTO adminApproveReturn(Long orderId) {
        OrderResponseDTO order = orderAdminClient.adminApproveReturn(orderId);
        log.info("Admin approved return for order {}", orderId);
        return order;
    }

    @Override
    public OrderResponseDTO adminRejectReturn(Long orderId, String reason) {
        OrderResponseDTO order = orderAdminClient.adminRejectReturn(orderId, reason);
        log.info("Admin rejected return for order {}", orderId);
        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ReviewAdminSummaryDTO> listAllReviews(Pageable pageable) {
        return reviewAdminClient.listAllReviews(pageable);
    }

    @Override
    public ReviewAdminSummaryDTO hideReview(Long reviewId) {
        ReviewAdminSummaryDTO review = reviewAdminClient.hideReview(reviewId);
        log.info("Admin hid review {}", reviewId);
        return review;
    }

    @Override
    public ReviewAdminSummaryDTO restoreReview(Long reviewId) {
        ReviewAdminSummaryDTO review = reviewAdminClient.restoreReview(reviewId);
        log.info("Admin restored review {}", reviewId);
        return review;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<AdminNotificationResponseDTO> listAdminNotifications(Boolean processed, Pageable pageable) {
        Page<AdminNotification> page = processed == null
                ? adminNotificationRepository.findAll(pageable)
                : adminNotificationRepository.findAllByProcessed(processed, pageable);
        return PageResponseDTO.from(page.map(this::toAdminNotificationResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationCountResponseDTO getAdminUnreadNotificationCount() {
        return NotificationCountResponseDTO.builder()
                .unreadCount(adminNotificationRepository.countByProcessedFalse())
                .build();
    }

    private AdminNotificationResponseDTO toAdminNotificationResponse(AdminNotification notification) {
        return AdminNotificationResponseDTO.builder()
                .id(notification.getId())
                .productId(notification.getProductId())
                .reportId(notification.getReportId())
                .title(notification.getTitle())
                .type(notification.getType())
                .message(notification.getMessage())
                .createdAt(notification.getCreatedAt())
                .processed(notification.isProcessed())
                .processedAt(notification.getProcessedAt())
                .processedBy(notification.getProcessedBy())
                .resolutionReason(notification.getResolutionReason())
                .build();
    }

    private String requireAdminUserId(String adminUserId) {
        if (!StringUtils.hasText(adminUserId)) {
            throw new MissingUserContextException();
        }
        return adminUserId.trim();
    }

    private String normalizeReason(String reason) {
        return StringUtils.hasText(reason) ? reason.trim() : null;
    }
}
