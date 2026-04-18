package com.hsmart.backend.infrastructure.messaging;

import com.hsmart.backend.application.dto.ChatMessageResponseDTO;
import com.hsmart.backend.application.dto.NotificationResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketMessagePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendChatMessage(String userId, ChatMessageResponseDTO payload) {
        messagingTemplate.convertAndSendToUser(userId, "/queue/messages", payload);
        log.debug("Published chat message {} to user {}", payload.getId(), userId);
    }

    public void sendNotification(String userId, NotificationResponseDTO payload) {
        messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", payload);
        log.debug("Published notification {} to user {}", payload.getId(), userId);
    }
}
