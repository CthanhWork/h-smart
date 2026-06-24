package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.NotificationRequestDTO;
import com.hsmart.backend.application.dto.NotificationCountResponseDTO;
import com.hsmart.backend.application.dto.NotificationResponseDTO;
import com.hsmart.backend.application.dto.PageResponseDTO;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
    NotificationResponseDTO createNotification(NotificationRequestDTO request);
    NotificationResponseDTO createChatNotification(String senderId, String receiverId, Long productId);
    List<NotificationResponseDTO> getNotificationsForCurrentUser(String currentUserId);
    PageResponseDTO<NotificationResponseDTO> getNotificationsPage(String currentUserId, Boolean read, String type, Pageable pageable);
    NotificationResponseDTO markNotificationAsRead(String currentUserId, String notificationId);
    NotificationCountResponseDTO markAllNotificationsAsRead(String currentUserId);
    NotificationCountResponseDTO getUnreadCount(String currentUserId);
}
