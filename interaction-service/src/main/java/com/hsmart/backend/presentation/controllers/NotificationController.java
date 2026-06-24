package com.hsmart.backend.presentation.controllers;

import com.hsmart.backend.application.dto.ApiResponse;
import com.hsmart.backend.application.dto.NotificationCountResponseDTO;
import com.hsmart.backend.application.dto.NotificationRequestDTO;
import com.hsmart.backend.application.dto.NotificationResponseDTO;
import com.hsmart.backend.application.dto.PageResponseDTO;
import com.hsmart.backend.application.exceptions.MissingUserContextException;
import com.hsmart.backend.infrastructure.context.UserContextHolder;
import com.hsmart.backend.service.NotificationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interactions/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<ApiResponse<NotificationResponseDTO>> createNotification(
            @Valid @RequestBody NotificationRequestDTO request
    ) {
        NotificationResponseDTO response = notificationService.createNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "Notification created successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponseDTO>>> getMyNotifications() {
        String currentUserId = requireCurrentUserId();
        List<NotificationResponseDTO> notifications = notificationService.getNotificationsForCurrentUser(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Notifications fetched successfully", notifications));
    }

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<PageResponseDTO<NotificationResponseDTO>>> getMyNotificationsPage(
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) String type,
            @ParameterObject
            @PageableDefault(sort = "timestamp", direction = Sort.Direction.DESC, size = 20) Pageable pageable
    ) {
        PageResponseDTO<NotificationResponseDTO> response = notificationService.getNotificationsPage(
                requireCurrentUserId(),
                read,
                type,
                pageable
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Notifications page fetched successfully", response));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationResponseDTO>> markNotificationAsRead(
            @PathVariable String notificationId
    ) {
        NotificationResponseDTO response = notificationService.markNotificationAsRead(
                requireCurrentUserId(),
                notificationId
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Notification marked as read", response));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<NotificationCountResponseDTO>> markAllNotificationsAsRead() {
        NotificationCountResponseDTO response = notificationService.markAllNotificationsAsRead(requireCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "All notifications marked as read", response));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<NotificationCountResponseDTO>> getUnreadCount() {
        NotificationCountResponseDTO response = notificationService.getUnreadCount(requireCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Unread notification count fetched successfully", response));
    }

    private String requireCurrentUserId() {
        String currentUserId = UserContextHolder.getCurrentUserId();
        if (!StringUtils.hasText(currentUserId)) {
            throw new MissingUserContextException();
        }
        return currentUserId;
    }
}
