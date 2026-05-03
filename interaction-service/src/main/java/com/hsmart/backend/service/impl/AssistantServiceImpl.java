package com.hsmart.backend.service.impl;

import com.hsmart.backend.application.dto.AssistantChatMessage;
import com.hsmart.backend.application.dto.AssistantProductContext;
import com.hsmart.backend.application.exceptions.InvalidInteractionRequestException;
import com.hsmart.backend.domain.entities.ChatMessage;
import com.hsmart.backend.infrastructure.config.AssistantProperties;
import com.hsmart.backend.infrastructure.persistence.ChatMessageRepository;
import com.hsmart.backend.service.AssistantModelClient;
import com.hsmart.backend.service.AssistantService;
import com.hsmart.backend.service.ProductContextService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantServiceImpl implements AssistantService {

    private static final String SYSTEM_ROLE = "system";
    private static final String USER_ROLE = "user";
    private static final String ASSISTANT_ROLE = "assistant";

    private final ChatMessageRepository chatMessageRepository;
    private final AssistantModelClient assistantModelClient;
    private final ProductContextService productContextService;
    private final AssistantProperties assistantProperties;
    private final Tracer tracer;

    @Override
    public String chat(String userId, String message) {
        if (!StringUtils.hasText(userId)) {
            throw new InvalidInteractionRequestException("userId is required");
        }
        if (!StringUtils.hasText(message)) {
            throw new InvalidInteractionRequestException("message is required");
        }

        String normalizedUserId = userId.trim();
        String normalizedMessage = message.trim();
        String traceId = currentTraceId();
        long startedAt = System.nanoTime();

        log.info("Started assistant chat request for user {} with traceId {}", normalizedUserId, traceId);

        List<ChatMessage> history = new ArrayList<>(chatMessageRepository.findAssistantConversationHistory(
                normalizedUserId,
                assistantProperties.assistantId(),
                PageRequest.of(0, resolveHistoryLimit())
        ));
        Collections.reverse(history);
        log.info("Loaded {} assistant history messages for user {} with traceId {}",
                history.size(), normalizedUserId, traceId);

        AssistantProductContext productContext = productContextService.buildContext(
                normalizedMessage,
                normalizedUserId,
                traceId
        );

        List<AssistantChatMessage> promptMessages = buildPromptMessages(
                history,
                normalizedMessage,
                normalizedUserId,
                productContext
        );
        long aiStartedAt = System.nanoTime();
        String assistantReply = assistantModelClient.generateReply(promptMessages);
        long aiDurationMs = elapsedMillis(aiStartedAt);
        assistantReply = applyRealtimeUnavailableNotice(assistantReply, productContext);

        saveConversationTurn(normalizedUserId, normalizedMessage, assistantReply);

        log.info("Completed assistant chat request for user {} with traceId {} in {} ms. AI response time was {} ms",
                normalizedUserId, traceId, elapsedMillis(startedAt), aiDurationMs);
        return assistantReply;
    }

    private List<AssistantChatMessage> buildPromptMessages(
            List<ChatMessage> history,
            String currentMessage,
            String userId,
            AssistantProductContext productContext
    ) {
        List<AssistantChatMessage> messages = new ArrayList<>();
        messages.add(new AssistantChatMessage(SYSTEM_ROLE, buildSystemPrompt(productContext)));

        for (ChatMessage chatMessage : history) {
            String role = assistantProperties.assistantId().equals(chatMessage.getSenderId())
                    ? ASSISTANT_ROLE
                    : USER_ROLE;
            if (StringUtils.hasText(chatMessage.getContent())) {
                messages.add(new AssistantChatMessage(role, chatMessage.getContent().trim()));
            }
        }

        messages.add(new AssistantChatMessage(USER_ROLE, currentMessage));
        log.debug("Built assistant prompt with {} messages for user {}", messages.size(), userId);
        return messages;
    }

    private String buildSystemPrompt(AssistantProductContext productContext) {
        if (productContext == null || !productContext.hasPromptAddition()) {
            return assistantProperties.systemPrompt();
        }

        return assistantProperties.systemPrompt() + "\n\n" + productContext.promptAddition();
    }

    private String applyRealtimeUnavailableNotice(String assistantReply, AssistantProductContext productContext) {
        if (productContext == null || !productContext.realtimeUnavailable()) {
            return assistantReply;
        }

        String requiredNotice = ProductContextServiceImpl.REALTIME_UNAVAILABLE_NOTICE;
        if (assistantReply != null && assistantReply.contains("không thể truy cập dữ liệu thời gian thực")) {
            return assistantReply;
        }

        return requiredNotice + "\n\n" + assistantReply;
    }

    private void saveConversationTurn(String userId, String userMessage, String assistantReply) {
        Instant now = Instant.now();
        ChatMessage question = ChatMessage.builder()
                .senderId(userId)
                .receiverId(assistantProperties.assistantId())
                .content(userMessage)
                .timestamp(now)
                .build();
        ChatMessage answer = ChatMessage.builder()
                .senderId(assistantProperties.assistantId())
                .receiverId(userId)
                .content(assistantReply)
                .timestamp(now.plusMillis(1))
                .build();

        chatMessageRepository.saveAll(List.of(question, answer));
        log.info("Stored assistant conversation turn for user {}", userId);
    }

    private int resolveHistoryLimit() {
        return Math.min(10, Math.max(5, assistantProperties.historyLimit()));
    }

    private String currentTraceId() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return "unavailable";
        }
        return span.context().traceId();
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
