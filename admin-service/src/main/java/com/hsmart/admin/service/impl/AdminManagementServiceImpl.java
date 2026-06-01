package com.hsmart.admin.service.impl;

import com.hsmart.admin.application.dto.ManualProductModerationRequestDTO;
import com.hsmart.admin.domain.entities.ManualProductModerationAction;
import com.hsmart.admin.infrastructure.persistence.AdminNotificationRepository;
import com.hsmart.admin.service.AdminManagementService;
import com.hsmart.admin.service.ProductAdminClient;
import com.hsmart.admin.service.UserAdminClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    public void banUser(String userId) {
        userAdminClient.updateUserActiveStatus(userId, false);
        log.info("Banned user {}", userId);
    }

    @Override
    public void moderateProduct(Long productId, ManualProductModerationRequestDTO request) {
        ManualProductModerationAction action = request.getAction();
        String productStatus = action == ManualProductModerationAction.APPROVE ? APPROVED_STATUS : HIDDEN_STATUS;
        productAdminClient.updateModerationStatus(productId, productStatus);
        int processedNotifications = adminNotificationRepository.markProcessedByProductIdAndType(
                productId,
                PENDING_REVIEW_NOTIFICATION_TYPE
        );
        log.info(
                "Completed manual moderation for product {} with action {} and resolved {} admin notifications",
                productId,
                action,
                processedNotifications
        );
    }
}
