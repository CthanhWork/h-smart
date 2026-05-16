package com.hsmart.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hsmart.backend.application.dto.AssistantChatMessage;
import com.hsmart.backend.application.dto.AssistantProductContext;
import com.hsmart.backend.application.dto.IntentClassification;
import com.hsmart.backend.application.dto.IntentClassification.Intent;
import com.hsmart.backend.application.dto.OrderSummary;
import com.hsmart.backend.domain.entities.ChatMessage;
import com.hsmart.backend.infrastructure.config.AssistantProperties;
import com.hsmart.backend.infrastructure.persistence.ChatMessageRepository;
import com.hsmart.backend.service.AssistantModelClient;
import com.hsmart.backend.service.IntentClassifier;
import com.hsmart.backend.service.OrderClient;
import com.hsmart.backend.service.PolicySearchService;
import com.hsmart.backend.service.ProductContextService;
import io.micrometer.tracing.Tracer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    private IntentClassifier intentClassifier;
    @Mock
    private OrderClient orderClient;
    @Mock
    private PolicySearchService policySearchService;
    @Mock
    private ProductContextService productContextService;
    @Mock
    private Tracer tracer;

    @Test
    void chatShouldSendContextAndStoreConversationTurn() {
        AssistantProperties properties = new AssistantProperties(
                "https://api.openai.com/v1",
                "test-key",
                "qwen3:4b-instruct",
                2_000,
                60_000,
                10,
                "h-smart-assistant",
                "You are H-Smart Assistant"
        );
        AssistantServiceImpl assistantService = new AssistantServiceImpl(
                chatMessageRepository,
                assistantModelClient,
                intentClassifier,
                orderClient,
                policySearchService,
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
        when(intentClassifier.classify("Tu van them giup minh"))
                .thenReturn(new IntentClassification(Intent.GENERAL, "General product advice"));
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
        when(intentClassifier.classify("Co may giat nao khong?"))
                .thenReturn(new IntentClassification(Intent.GENERAL, "General product advice"));
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
        when(intentClassifier.classify("Tim tu lanh cu"))
                .thenReturn(new IntentClassification(Intent.GENERAL, "General product advice"));
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

    @Test
    void chatShouldUseLatestOrderContextForSystemIntent() {
        AssistantServiceImpl assistantService = newAssistantService();

        when(chatMessageRepository.findAssistantConversationHistory(
                eq("user-1"),
                eq("h-smart-assistant"),
                any(Pageable.class)
        )).thenReturn(List.of());
        when(intentClassifier.classify("Don hang gan nhat cua toi sao roi?"))
                .thenReturn(new IntentClassification(Intent.SYSTEM, "User asks about latest order"));
        when(orderClient.findLatestOrder("user-1"))
                .thenReturn(Optional.of(new OrderSummary(
                        7L,
                        "user-1",
                        "seller-1",
                        99L,
                        BigDecimal.valueOf(250000),
                        "COMPLETED",
                        LocalDateTime.parse("2026-05-14T10:00:00"),
                        LocalDateTime.parse("2026-05-14T10:05:00")
                )));
        when(assistantModelClient.generateReply(any())).thenReturn("Don hang gan nhat cua ban da hoan tat.");

        assistantService.chat("user-1", "Don hang gan nhat cua toi sao roi?");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AssistantChatMessage>> promptCaptor = ArgumentCaptor.forClass(List.class);
        verify(assistantModelClient).generateReply(promptCaptor.capture());

        String systemPrompt = promptCaptor.getValue().get(0).content();
        assertEquals(true, systemPrompt.contains("Latest order details"));
        assertEquals(true, systemPrompt.contains("orderId=7"));
        assertEquals(true, systemPrompt.contains("status=COMPLETED"));
        verifyNoInteractions(productContextService);
        verifyNoInteractions(policySearchService);
    }

    @Test
    void chatShouldUsePolicySearchContextForPolicyIntent() {
        AssistantServiceImpl assistantService = newAssistantService();

        when(chatMessageRepository.findAssistantConversationHistory(
                eq("user-1"),
                eq("h-smart-assistant"),
                any(Pageable.class)
        )).thenReturn(List.of());
        when(intentClassifier.classify("Chinh sach doi tra nhu the nao?"))
                .thenReturn(new IntentClassification(Intent.POLICY, "User asks about return policy"));
        when(policySearchService.findRelevantPolicyChunks("Chinh sach doi tra nhu the nao?"))
                .thenReturn(List.of("Title: Return policy\nContent: Buyers can request support when the item does not match the listing."));
        when(assistantModelClient.generateReply(any())).thenReturn("Ban co the yeu cau ho tro doi tra neu san pham khong dung mo ta.");

        assistantService.chat("user-1", "Chinh sach doi tra nhu the nao?");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AssistantChatMessage>> promptCaptor = ArgumentCaptor.forClass(List.class);
        verify(assistantModelClient).generateReply(promptCaptor.capture());

        String systemPrompt = promptCaptor.getValue().get(0).content();
        assertEquals(true, systemPrompt.contains("hsmart-policy-index"));
        assertEquals(true, systemPrompt.contains("Return policy"));
        assertEquals(true, systemPrompt.contains("does not match the listing"));
        verifyNoInteractions(productContextService);
        verifyNoInteractions(orderClient);
    }

    private AssistantServiceImpl newAssistantService() {
        AssistantProperties properties = new AssistantProperties(
                "https://api.openai.com/v1",
                "test-key",
                "qwen2.5:3b-instruct",
                2_000,
                60_000,
                10,
                "h-smart-assistant",
                "You are H-Smart Assistant"
        );
        return new AssistantServiceImpl(
                chatMessageRepository,
                assistantModelClient,
                intentClassifier,
                orderClient,
                policySearchService,
                productContextService,
                properties,
                tracer
        );
    }
}
