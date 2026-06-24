package com.hsmart.admin.presentation.controllers;

import com.hsmart.admin.application.dto.ApiResponse;
import com.hsmart.admin.application.dto.AdminNotificationResponseDTO;
import com.hsmart.admin.application.dto.ManualProductModerationRequestDTO;
import com.hsmart.admin.application.dto.NotificationCountResponseDTO;
import com.hsmart.admin.application.dto.OrderResponseDTO;
import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.application.dto.ReviewAdminSummaryDTO;
import com.hsmart.admin.application.dto.UserAdminSummaryDTO;
import com.hsmart.admin.service.AdminManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminManagementController {

    private final AdminManagementService adminManagementService;

    @PostMapping("/users/{userId}/ban")
    public ResponseEntity<ApiResponse<Void>> banUser(@PathVariable String userId) {
        adminManagementService.banUser(userId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "User banned successfully", null));
    }

    @PostMapping("/users/{userId}/unban")
    public ResponseEntity<ApiResponse<Void>> unbanUser(@PathVariable String userId) {
        adminManagementService.unbanUser(userId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "User unbanned successfully", null));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponseDTO<UserAdminSummaryDTO>>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            Pageable pageable
    ) {
        PageResponseDTO<UserAdminSummaryDTO> response = adminManagementService.listUsers(search, isActive, pageable);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Users fetched successfully", response));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<UserAdminSummaryDTO>> getUserById(@PathVariable Long userId) {
        UserAdminSummaryDTO response = adminManagementService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "User fetched successfully", response));
    }

    @PostMapping("/products/{productId}/moderate")
    public ResponseEntity<ApiResponse<Void>> moderateProduct(
            @PathVariable Long productId,
            @RequestHeader(name = "X-User-Id", required = false) String adminUserId,
            @Valid @RequestBody ManualProductModerationRequestDTO request
    ) {
        adminManagementService.moderateProduct(productId, request, adminUserId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Product moderated successfully", null));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<PageResponseDTO<OrderResponseDTO>>> listAllOrders(
            @RequestParam(required = false) String status,
            Pageable pageable
    ) {
        PageResponseDTO<OrderResponseDTO> response = adminManagementService.listAllOrders(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Orders fetched successfully", response));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> adminCancelOrder(@PathVariable Long orderId) {
        OrderResponseDTO response = adminManagementService.adminCancelOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Order cancelled successfully", response));
    }

    @PostMapping("/orders/{orderId}/return-approve")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> adminApproveReturn(@PathVariable Long orderId) {
        OrderResponseDTO response = adminManagementService.adminApproveReturn(orderId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Return approved successfully", response));
    }

    @PostMapping("/orders/{orderId}/return-reject")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> adminRejectReturn(
            @PathVariable Long orderId,
            @RequestBody(required = false) java.util.Map<String, String> body
    ) {
        String reason = body == null ? null : body.get("reason");
        OrderResponseDTO response = adminManagementService.adminRejectReturn(orderId, reason);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Return rejected successfully", response));
    }

    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<PageResponseDTO<ReviewAdminSummaryDTO>>> listAllReviews(Pageable pageable) {
        PageResponseDTO<ReviewAdminSummaryDTO> response = adminManagementService.listAllReviews(pageable);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Reviews fetched successfully", response));
    }

    @PostMapping("/reviews/{reviewId}/hide")
    public ResponseEntity<ApiResponse<ReviewAdminSummaryDTO>> hideReview(@PathVariable Long reviewId) {
        ReviewAdminSummaryDTO response = adminManagementService.hideReview(reviewId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Review hidden successfully", response));
    }

    @PostMapping("/reviews/{reviewId}/restore")
    public ResponseEntity<ApiResponse<ReviewAdminSummaryDTO>> restoreReview(@PathVariable Long reviewId) {
        ReviewAdminSummaryDTO response = adminManagementService.restoreReview(reviewId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Review restored successfully", response));
    }

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<PageResponseDTO<AdminNotificationResponseDTO>>> listAdminNotifications(
            @RequestParam(required = false) Boolean processed,
            @ParameterObject
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC, size = 20) Pageable pageable
    ) {
        PageResponseDTO<AdminNotificationResponseDTO> response =
                adminManagementService.listAdminNotifications(processed, pageable);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Admin notifications fetched successfully", response));
    }

    @GetMapping("/notifications/unread-count")
    public ResponseEntity<ApiResponse<NotificationCountResponseDTO>> getAdminUnreadNotificationCount() {
        NotificationCountResponseDTO response = adminManagementService.getAdminUnreadNotificationCount();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Admin unread notification count fetched successfully", response));
    }
}
