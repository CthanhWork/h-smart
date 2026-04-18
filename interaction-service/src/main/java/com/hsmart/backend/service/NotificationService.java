package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.NotificationRequestDTO;
import com.hsmart.backend.application.dto.NotificationResponseDTO;
import java.util.List;

public interface NotificationService {
    NotificationResponseDTO createNotification(NotificationRequestDTO request);
    NotificationResponseDTO createChatNotification(String senderId, String receiverId, Long productId);
    List<NotificationResponseDTO> getNotificationsForCurrentUser(String currentUserId);
}
