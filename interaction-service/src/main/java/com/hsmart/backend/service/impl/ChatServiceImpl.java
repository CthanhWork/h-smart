package com.hsmart.backend.service.impl;

import com.hsmart.backend.application.dto.ChatMessageRequestDTO;
import com.hsmart.backend.application.dto.ChatMessageResponseDTO;
import com.hsmart.backend.application.exceptions.InvalidInteractionRequestException;
import com.hsmart.backend.application.mapper.ChatMessageMapper;
import com.hsmart.backend.domain.entities.ChatMessage;
import com.hsmart.backend.infrastructure.messaging.WebSocketMessagePublisher;
import com.hsmart.backend.infrastructure.persistence.ChatMessageRepository;
import com.hsmart.backend.service.ChatService;
import com.hsmart.backend.service.NotificationService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageMapper chatMessageMapper;
    private final NotificationService notificationService;
    private final WebSocketMessagePublisher messagePublisher;

    @Override
    public ChatMessageResponseDTO processIncomingMessage(String senderId, ChatMessageRequestDTO request) {
        validateParticipants(senderId, request.getReceiverId());

        ChatMessage chatMessage = ChatMessage.builder()
                .senderId(senderId.trim())
                .receiverId(request.getReceiverId().trim())
                .productId(request.getProductId())
                .content(request.getContent().trim())
                .timestamp(Instant.now())
                .build();

        ChatMessage saved = chatMessageRepository.save(chatMessage);
        ChatMessageResponseDTO response = chatMessageMapper.toResponse(saved);

        messagePublisher.sendChatMessage(senderId, response);
        messagePublisher.sendChatMessage(response.getReceiverId(), response);
        notificationService.createChatNotification(senderId, response.getReceiverId(), response.getProductId());

        log.info("Stored chat message {} from {} to {} for product {}",
                response.getId(), response.getSenderId(), response.getReceiverId(), response.getProductId());
        return response;
    }

    @Override
    public List<ChatMessageResponseDTO> getConversation(String currentUserId, String participantId, Long productId) {
        if (!StringUtils.hasText(participantId)) {
            throw new InvalidInteractionRequestException("participantId is required");
        }
        if (productId == null) {
            throw new InvalidInteractionRequestException("productId is required");
        }

        return chatMessageRepository.findConversation(productId, currentUserId, participantId.trim()).stream()
                .map(chatMessageMapper::toResponse)
                .toList();
    }

    private void validateParticipants(String senderId, String receiverId) {
        if (!StringUtils.hasText(senderId)) {
            throw new InvalidInteractionRequestException("senderId is required");
        }
        if (!StringUtils.hasText(receiverId)) {
            throw new InvalidInteractionRequestException("receiverId is required");
        }
        if (senderId.trim().equals(receiverId.trim())) {
            throw new InvalidInteractionRequestException("senderId and receiverId must be different");
        }
    }
}
