package com.hsmart.backend.service.impl;

import com.hsmart.backend.application.dto.NotificationRequestDTO;
import com.hsmart.backend.application.dto.NotificationResponseDTO;
import com.hsmart.backend.application.mapper.NotificationMapper;
import com.hsmart.backend.domain.entities.Notification;
import com.hsmart.backend.infrastructure.messaging.WebSocketMessagePublisher;
import com.hsmart.backend.infrastructure.persistence.NotificationRepository;
import com.hsmart.backend.service.NotificationService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final WebSocketMessagePublisher messagePublisher;

    @Override
    public NotificationResponseDTO createNotification(NotificationRequestDTO request) {
        Notification notification = Notification.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .message(request.getMessage())
                .productId(request.getProductId())
                .read(false)
                .timestamp(Instant.now())
                .build();

        Notification saved = notificationRepository.save(notification);
        NotificationResponseDTO response = notificationMapper.toResponse(saved);
        messagePublisher.sendNotification(response.getUserId(), response);
        log.info("Created notification {} for user {}", response.getId(), response.getUserId());
        return response;
    }

    @Override
    public NotificationResponseDTO createChatNotification(String senderId, String receiverId, Long productId) {
        String message = String.format("You have a new message from %s about product %d", senderId, productId);
        NotificationRequestDTO request = NotificationRequestDTO.builder()
                .userId(receiverId)
                .type("CHAT")
                .message(message)
                .productId(productId)
                .build();
        return createNotification(request);
    }

    @Override
    public List<NotificationResponseDTO> getNotificationsForCurrentUser(String currentUserId) {
        return notificationRepository.findByUserIdOrderByTimestampDesc(currentUserId).stream()
                .map(notificationMapper::toResponse)
                .toList();
    }
}
