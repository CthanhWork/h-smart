package com.hsmart.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hsmart.backend.application.dto.ChatMessageRequestDTO;
import com.hsmart.backend.application.dto.ChatMessageResponseDTO;
import com.hsmart.backend.application.exceptions.InvalidInteractionRequestException;
import com.hsmart.backend.application.mapper.ChatMessageMapper;
import com.hsmart.backend.domain.entities.ChatMessage;
import com.hsmart.backend.domain.entities.ChatMessageType;
import com.hsmart.backend.infrastructure.config.StorageProperties;
import com.hsmart.backend.infrastructure.messaging.WebSocketMessagePublisher;
import com.hsmart.backend.infrastructure.persistence.ChatMessageRepository;
import com.hsmart.backend.service.NotificationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatMessageMapper chatMessageMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private WebSocketMessagePublisher messagePublisher;
    @Mock
    private StorageProperties storageProperties;

    @InjectMocks
    private ChatServiceImpl chatService;

    @TempDir
    Path tempDir;

    private ChatMessageRequestDTO request;
    private ChatMessage savedMessage;
    private ChatMessageResponseDTO response;

    @BeforeEach
    void setUp() {
        request = ChatMessageRequestDTO.builder()
                .receiverId("seller-2")
                .productId(10L)
                .content("Is this product still available?")
                .build();

        savedMessage = ChatMessage.builder()
                .id("message-1")
                .senderId("buyer-1")
                .receiverId("seller-2")
                .productId(10L)
                .content("Is this product still available?")
                .timestamp(Instant.now())
                .build();

        response = ChatMessageResponseDTO.builder()
                .id("message-1")
                .senderId("buyer-1")
                .receiverId("seller-2")
                .productId(10L)
                .content("Is this product still available?")
                .timestamp(savedMessage.getTimestamp())
                .build();
    }

    @Test
    void processIncomingMessageShouldPersistAndPublish() {
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedMessage);
        when(chatMessageMapper.toResponse(savedMessage)).thenReturn(response);

        ChatMessageResponseDTO result = chatService.processIncomingMessage("buyer-1", request);

        assertEquals("message-1", result.getId());
        verify(chatMessageRepository, times(1)).save(any(ChatMessage.class));
        verify(messagePublisher, times(1)).sendChatMessage("buyer-1", response);
        verify(messagePublisher, times(1)).sendChatMessage("seller-2", response);
        verify(notificationService, times(1)).createChatNotification("buyer-1", "seller-2", 10L);
    }

    @Test
    void processIncomingMessageShouldRejectSameSenderAndReceiver() {
        ChatMessageRequestDTO invalidRequest = ChatMessageRequestDTO.builder()
                .receiverId("buyer-1")
                .productId(10L)
                .content("Invalid message")
                .build();

        assertThrows(InvalidInteractionRequestException.class,
                () -> chatService.processIncomingMessage("buyer-1", invalidRequest));
    }

    @Test
    void processMediaMessageShouldPersistStoreAndPublishImage() throws Exception {
        when(storageProperties.uploadDir()).thenReturn(tempDir.toString());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "chat-photo.jpg",
                "image/jpeg",
                "fake-image".getBytes()
        );

        ChatMessage savedMediaMessage = ChatMessage.builder()
                .id("message-2")
                .messageType(ChatMessageType.IMAGE)
                .senderId("buyer-1")
                .receiverId("seller-2")
                .productId(10L)
                .content("Xem ảnh nhé")
                .mediaUrl("/api/v1/interactions/media/generated.jpg")
                .mediaMimeType("image/jpeg")
                .mediaSizeBytes((long) file.getBytes().length)
                .mediaOriginalFilename("chat-photo.jpg")
                .timestamp(Instant.now())
                .build();

        ChatMessageResponseDTO mediaResponse = ChatMessageResponseDTO.builder()
                .id("message-2")
                .messageType(ChatMessageType.IMAGE)
                .senderId("buyer-1")
                .receiverId("seller-2")
                .productId(10L)
                .content("Xem ảnh nhé")
                .mediaUrl("/api/v1/interactions/media/generated.jpg")
                .mediaMimeType("image/jpeg")
                .mediaSizeBytes((long) file.getBytes().length)
                .mediaOriginalFilename("chat-photo.jpg")
                .timestamp(savedMediaMessage.getTimestamp())
                .build();

        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedMediaMessage);
        when(chatMessageMapper.toResponse(savedMediaMessage)).thenReturn(mediaResponse);

        ChatMessageResponseDTO result = chatService.processMediaMessage(
                "buyer-1",
                "seller-2",
                10L,
                "Xem ảnh nhé",
                file
        );

        assertEquals(ChatMessageType.IMAGE, result.getMessageType());
        assertEquals("/api/v1/interactions/media/generated.jpg", result.getMediaUrl());
        assertTrue(Files.list(tempDir).findAny().isPresent());
        verify(chatMessageRepository, times(1)).save(any(ChatMessage.class));
        verify(messagePublisher, times(1)).sendChatMessage("buyer-1", mediaResponse);
        verify(messagePublisher, times(1)).sendChatMessage("seller-2", mediaResponse);
        verify(notificationService, times(1)).createChatNotification("buyer-1", "seller-2", 10L);
    }

    @Test
    void processMediaMessageShouldRejectOversizedVideo() {
        byte[] bytes = new byte[(int) (21L * 1024 * 1024)];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "clip.mp4",
                "video/mp4",
                bytes
        );

        InvalidInteractionRequestException exception = assertThrows(
                InvalidInteractionRequestException.class,
                () -> chatService.processMediaMessage("buyer-1", "seller-2", 10L, null, file)
        );

        assertEquals("File exceeds the maximum allowed size of 20MB", exception.getMessage());
    }
}
