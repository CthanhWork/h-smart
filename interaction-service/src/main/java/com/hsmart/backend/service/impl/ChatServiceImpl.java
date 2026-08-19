package com.hsmart.backend.service.impl;

import com.hsmart.backend.application.dto.ChatMessageRequestDTO;
import com.hsmart.backend.application.dto.ChatMessageResponseDTO;
import com.hsmart.backend.application.dto.ChatConversationSummaryDTO;
import com.hsmart.backend.application.exceptions.InvalidInteractionRequestException;
import com.hsmart.backend.application.mapper.ChatMessageMapper;
import com.hsmart.backend.domain.entities.ChatMessage;
import com.hsmart.backend.domain.entities.ChatMessageType;
import com.hsmart.backend.infrastructure.config.StorageProperties;
import com.hsmart.backend.infrastructure.messaging.WebSocketMessagePublisher;
import com.hsmart.backend.infrastructure.persistence.ChatMessageRepository;
import com.hsmart.backend.service.ChatService;
import com.hsmart.backend.service.NotificationService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_VIDEO_SIZE_BYTES = 20L * 1024 * 1024;
    private static final int MAX_MEDIA_CAPTION_LENGTH = 1000;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4",
            "video/webm"
    );

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageMapper chatMessageMapper;
    private final NotificationService notificationService;
    private final WebSocketMessagePublisher messagePublisher;
    private final StorageProperties storageProperties;

    @Override
    public ChatMessageResponseDTO processIncomingMessage(String senderId, ChatMessageRequestDTO request) {
        validateParticipants(senderId, request.getReceiverId());

        ChatMessage chatMessage = ChatMessage.builder()
                .messageType(ChatMessageType.TEXT)
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
    public ChatMessageResponseDTO processMediaMessage(
            String senderId,
            String receiverId,
            Long productId,
            String content,
            MultipartFile file
    ) {
        validateParticipants(senderId, receiverId);
        if (productId == null) {
            throw new InvalidInteractionRequestException("productId is required");
        }
        if (file == null || file.isEmpty()) {
            throw new InvalidInteractionRequestException("file is required");
        }

        ChatMessageType messageType = resolveMessageType(file);
        validateMediaFile(file, messageType);
        String normalizedContent = normalizeOptionalContent(content);
        String mediaUrl = saveMediaFile(file, messageType);

        ChatMessage chatMessage = ChatMessage.builder()
                .messageType(messageType)
                .senderId(senderId.trim())
                .receiverId(receiverId.trim())
                .productId(productId)
                .content(normalizedContent)
                .mediaUrl(mediaUrl)
                .mediaMimeType(file.getContentType().trim())
                .mediaSizeBytes(file.getSize())
                .mediaOriginalFilename(normalizeOriginalFilename(file.getOriginalFilename()))
                .timestamp(Instant.now())
                .build();

        ChatMessage saved = chatMessageRepository.save(chatMessage);
        ChatMessageResponseDTO response = chatMessageMapper.toResponse(saved);

        messagePublisher.sendChatMessage(response.getSenderId(), response);
        messagePublisher.sendChatMessage(response.getReceiverId(), response);
        notificationService.createChatNotification(response.getSenderId(), response.getReceiverId(), response.getProductId());

        log.info("Stored {} chat media message {} from {} to {} for product {}",
                response.getMessageType(), response.getId(), response.getSenderId(), response.getReceiverId(), response.getProductId());
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

    @Override
    public List<ChatConversationSummaryDTO> getConversationSummaries(String currentUserId) {
        if (!StringUtils.hasText(currentUserId)) {
            throw new InvalidInteractionRequestException("currentUserId is required");
        }

        String normalizedUserId = currentUserId.trim();
        Map<String, ChatConversationSummaryDTO> summaries = new LinkedHashMap<>();

        for (ChatMessage message : chatMessageRepository.findUserMessages(normalizedUserId)) {
            if (message.getProductId() == null) {
                continue;
            }

            String participantId = resolveParticipantId(normalizedUserId, message);
            if (!StringUtils.hasText(participantId) || "h-smart-assistant".equals(participantId)) {
                continue;
            }

            String key = participantId + ":" + message.getProductId();
            summaries.putIfAbsent(key, ChatConversationSummaryDTO.builder()
                    .participantId(participantId)
                    .productId(message.getProductId())
                    .lastMessage(buildConversationPreview(message))
                    .updatedAt(message.getTimestamp())
                    .build());
        }

        log.info("Loaded {} chat conversation summaries for user {}", summaries.size(), normalizedUserId);
        return List.copyOf(summaries.values());
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

    private ChatMessageType resolveMessageType(MultipartFile file) {
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType)) {
            throw new InvalidInteractionRequestException("Unsupported media type");
        }

        String normalizedContentType = contentType.trim().toLowerCase();
        if (ALLOWED_IMAGE_TYPES.contains(normalizedContentType)) {
            return ChatMessageType.IMAGE;
        }
        if (ALLOWED_VIDEO_TYPES.contains(normalizedContentType)) {
            return ChatMessageType.VIDEO;
        }
        throw new InvalidInteractionRequestException("Only JPEG, PNG, WebP images or MP4/WebM videos are supported");
    }

    private void validateMediaFile(MultipartFile file, ChatMessageType messageType) {
        long fileSize = file.getSize();
        if (fileSize <= 0) {
            throw new InvalidInteractionRequestException("file must not be empty");
        }

        long maxSizeBytes = messageType == ChatMessageType.IMAGE ? MAX_IMAGE_SIZE_BYTES : MAX_VIDEO_SIZE_BYTES;
        if (fileSize > maxSizeBytes) {
            String limitLabel = messageType == ChatMessageType.IMAGE ? "5MB" : "20MB";
            throw new InvalidInteractionRequestException("File exceeds the maximum allowed size of " + limitLabel);
        }
    }

    private String normalizeOptionalContent(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }

        String normalized = content.trim();
        if (normalized.length() > MAX_MEDIA_CAPTION_LENGTH) {
            throw new InvalidInteractionRequestException(
                    "content must not exceed " + MAX_MEDIA_CAPTION_LENGTH + " characters for media messages"
            );
        }
        return normalized;
    }

    private String saveMediaFile(MultipartFile file, ChatMessageType messageType) {
        String originalFilename = normalizeOriginalFilename(file.getOriginalFilename());
        String extension = resolveExtension(originalFilename, messageType, file.getContentType());
        String storedFilename = UUID.randomUUID() + extension;
        Path uploadDir = Paths.get(storageProperties.uploadDir()).toAbsolutePath().normalize();
        Path targetPath = uploadDir.resolve(storedFilename).normalize();

        if (!targetPath.startsWith(uploadDir)) {
            throw new InvalidInteractionRequestException("Invalid upload path");
        }

        try {
            Files.createDirectories(uploadDir);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new InvalidInteractionRequestException("Unable to store uploaded media");
        }

        return "/api/v1/interactions/media/" + storedFilename;
    }

    private String resolveExtension(String originalFilename, ChatMessageType messageType, String contentType) {
        if (StringUtils.hasText(originalFilename)) {
            int lastDotIndex = originalFilename.lastIndexOf('.');
            if (lastDotIndex >= 0) {
                return originalFilename.substring(lastDotIndex).toLowerCase();
            }
        }

        return switch (messageType) {
            case IMAGE -> "image/webp".equalsIgnoreCase(contentType) ? ".webp"
                    : "image/png".equalsIgnoreCase(contentType) ? ".png" : ".jpg";
            case VIDEO -> "video/webm".equalsIgnoreCase(contentType) ? ".webm" : ".mp4";
            case TEXT -> "";
        };
    }

    private String normalizeOriginalFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return null;
        }
        return Paths.get(originalFilename.trim()).getFileName().toString();
    }

    private String buildConversationPreview(ChatMessage message) {
        if (StringUtils.hasText(message.getContent())) {
            return message.getContent();
        }
        if (ChatMessageType.IMAGE.equals(message.getMessageType())) {
            return "Đã gửi một ảnh";
        }
        if (ChatMessageType.VIDEO.equals(message.getMessageType())) {
            return "Đã gửi một video";
        }
        return "";
    }

    private String resolveParticipantId(String currentUserId, ChatMessage message) {
        if (currentUserId.equals(message.getSenderId())) {
            return message.getReceiverId();
        }
        return message.getSenderId();
    }
}
