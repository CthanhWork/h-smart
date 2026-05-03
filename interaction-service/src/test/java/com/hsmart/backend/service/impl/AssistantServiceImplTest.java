package com.hsmart.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hsmart.backend.application.dto.AssistantChatMessage;
import com.hsmart.backend.application.dto.AssistantProductContext;
import com.hsmart.backend.domain.entities.ChatMessage;
import com.hsmart.backend.infrastructure.config.AssistantProperties;
import com.hsmart.backend.infrastructure.persistence.ChatMessageRepository;
import com.hsmart.backend.service.AssistantModelClient;
import com.hsmart.backend.service.ProductContextService;
import io.micrometer.tracing.Tracer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AssistantServiceImplTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private AssistantModelClient assistantModelClient;
    @Mock
    private ProductContextService productContextService;
    @Mock
    private Tracer tracer;

    @Test
    void chatShouldSendContextAndStoreConversationTurn() {
        AssistantProperties properties = new AssistantProperties(
                "http://localhost:11434",
                "qwen3:4b-instruct",
                10,
                "h-smart-assistant",
                "You are H-Smart Assistant"
        );
        AssistantServiceImpl assistantService = new AssistantServiceImpl(
                chatMessageRepository,
                assistantModelClient,
                productContextService,
                properties,
                tracer
        );

        List<ChatMessage> newestFirstHistory = List.of(
                ChatMessage.builder()
                        .senderId("h-smart-assistant")
                        .receiverId("user-1")
                        .content("May giat tiet kiem dien hon cho phong tro.")
                        .timestamp(Instant.parse("2026-05-02T02:00:00Z"))
                        .build(),
                ChatMessage.builder()
                        .senderId("user-1")
                        .receiverId("h-smart-assistant")
                        .content("Nen mua may giat hay tu lanh cu truoc?")
                        .timestamp(Instant.parse("2026-05-02T01:59:00Z"))
                        .build()
        );

        when(chatMessageRepository.findAssistantConversationHistory(
                eq("user-1"),
                eq("h-smart-assistant"),
                any(Pageable.class)
        )).thenReturn(newestFirstHistory);
        when(productContextService.buildContext(eq("Tu van them giup minh"), eq("user-1"), any()))
                .thenReturn(new AssistantProductContext(List.of(), "", false, 0));
        when(assistantModelClient.generateReply(any())).thenReturn("Nen xem may giat truoc neu ban giat do thuong xuyen.");

        String reply = assistantService.chat(" user-1 ", " Tu van them giup minh ");

        assertEquals("Nen xem may giat truoc neu ban giat do thuong xuyen.", reply);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AssistantChatMessage>> promptCaptor = ArgumentCaptor.forClass(List.class);
        verify(assistantModelClient).generateReply(promptCaptor.capture());
        List<AssistantChatMessage> prompt = promptCaptor.getValue();

        assertEquals("system", prompt.get(0).role());
        assertEquals("user", prompt.get(1).role());
        assertEquals("Nen mua may giat hay tu lanh cu truoc?", prompt.get(1).content());
        assertEquals("assistant", prompt.get(2).role());
        assertEquals("May giat tiet kiem dien hon cho phong tro.", prompt.get(2).content());
        assertEquals("user", prompt.get(3).role());
        assertEquals("Tu van them giup minh", prompt.get(3).content());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ChatMessage>> savedCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(chatMessageRepository, times(1)).saveAll(savedCaptor.capture());
        List<ChatMessage> savedMessages = new ArrayList<>();
        savedCaptor.getValue().forEach(savedMessages::add);

        assertEquals(2, savedMessages.size());
        assertEquals("user-1", savedMessages.get(0).getSenderId());
        assertEquals("h-smart-assistant", savedMessages.get(0).getReceiverId());
        assertEquals("Tu van them giup minh", savedMessages.get(0).getContent());
        assertEquals("h-smart-assistant", savedMessages.get(1).getSenderId());
        assertEquals("user-1", savedMessages.get(1).getReceiverId());
        assertEquals("Nen xem may giat truoc neu ban giat do thuong xuyen.", savedMessages.get(1).getContent());
    }

    @Test
    void chatShouldAppendProductContextToSystemPrompt() {
        AssistantServiceImpl assistantService = newAssistantService();

        when(chatMessageRepository.findAssistantConversationHistory(
                eq("user-1"),
                eq("h-smart-assistant"),
                any(Pageable.class)
        )).thenReturn(List.of());
        when(productContextService.buildContext(eq("Co may giat nao khong?"), eq("user-1"), any()))
                .thenReturn(new AssistantProductContext(
                        List.of("máy giặt"),
                        "Dưới đây là dữ liệu thực tế từ kho hàng H-Smart: Sản phẩm hiện có: Máy giặt mini - Giá: 900.000 ₫. Hãy sử dụng thông tin này để trả lời người dùng một cách chính xác nhất.",
                        false,
                        1
                ));
        when(assistantModelClient.generateReply(any())).thenReturn("Hien co may giat mini phu hop.");

        assistantService.chat("user-1", "Co may giat nao khong?");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AssistantChatMessage>> promptCaptor = ArgumentCaptor.forClass(List.class);
        verify(assistantModelClient).generateReply(promptCaptor.capture());

        String systemPrompt = promptCaptor.getValue().get(0).content();
        assertEquals(true, systemPrompt.contains("You are H-Smart Assistant"));
        assertEquals(true, systemPrompt.contains("Dưới đây là dữ liệu thực tế từ kho hàng H-Smart"));
        assertEquals(true, systemPrompt.contains("Máy giặt mini"));
    }

    @Test
    void chatShouldPrefixRealtimeUnavailableNoticeWhenProductCatalogFails() {
        AssistantServiceImpl assistantService = newAssistantService();

        when(chatMessageRepository.findAssistantConversationHistory(
                eq("user-1"),
                eq("h-smart-assistant"),
                any(Pageable.class)
        )).thenReturn(List.of());
        when(productContextService.buildContext(eq("Tim tu lanh cu"), eq("user-1"), any()))
                .thenReturn(new AssistantProductContext(
                        List.of("tủ lạnh"),
                        ProductContextServiceImpl.REALTIME_UNAVAILABLE_NOTICE,
                        true,
                        0
                ));
        when(assistantModelClient.generateReply(any())).thenReturn("Ban nen kiem tra ron cua va do lanh.");

        String reply = assistantService.chat("user-1", "Tim tu lanh cu");

        assertEquals(
                ProductContextServiceImpl.REALTIME_UNAVAILABLE_NOTICE + "\n\nBan nen kiem tra ron cua va do lanh.",
                reply
        );
    }

    private AssistantServiceImpl newAssistantService() {
        AssistantProperties properties = new AssistantProperties(
                "http://localhost:11434",
                "qwen3:4b-instruct",
                10,
                "h-smart-assistant",
                "You are H-Smart Assistant"
        );
        return new AssistantServiceImpl(
                chatMessageRepository,
                assistantModelClient,
                productContextService,
                properties,
                tracer
        );
    }
}
