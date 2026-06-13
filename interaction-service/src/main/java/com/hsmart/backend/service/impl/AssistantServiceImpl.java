package com.hsmart.backend.service.impl;

import com.hsmart.backend.application.dto.AssistantChatMessage;
import com.hsmart.backend.application.dto.AssistantPromptContext;
import com.hsmart.backend.application.dto.AssistantProductContext;
import com.hsmart.backend.application.dto.ChatMessageResponseDTO;
import com.hsmart.backend.application.dto.IntentClassification;
import com.hsmart.backend.application.dto.IntentClassification.Intent;
import com.hsmart.backend.application.dto.OrderSummary;
import com.hsmart.backend.application.dto.ProductDescriptionRequest;
import com.hsmart.backend.application.dto.ProductDescriptionResponse;
import com.hsmart.backend.application.exceptions.AssistantGatewayTimeoutException;
import com.hsmart.backend.application.exceptions.AssistantServiceUnavailableException;
import com.hsmart.backend.application.exceptions.InvalidInteractionRequestException;
import com.hsmart.backend.domain.entities.ChatMessage;
import com.hsmart.backend.infrastructure.config.AssistantProperties;
import com.hsmart.backend.infrastructure.persistence.ChatMessageRepository;
import com.hsmart.backend.service.AssistantModelClient;
import com.hsmart.backend.service.AssistantService;
import com.hsmart.backend.service.IntentClassifier;
import com.hsmart.backend.service.OrderClient;
import com.hsmart.backend.service.PolicySearchService;
import com.hsmart.backend.service.ProductContextService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
    private static final Locale VIETNAM_LOCALE = Locale.forLanguageTag("vi-VN");
    private static final String PRODUCT_DESCRIPTION_SYSTEM_PROMPT = "You are an expert copywriter for H-Smart, "
            + "a C2C marketplace for second-hand household appliances in Vietnam. Your task is to write a catchy, "
            + "honest, and SEO-friendly editable draft product description in Vietnamese based on the provided "
            + "details. Return one natural sales-oriented paragraph of about 45 to 60 Vietnamese words. "
            + "Make it useful for a buyer who is evaluating the item details. "
            + "Do not invent brand, model, size, quality level, working condition, warranty, accessories, defects, "
            + "contact info, seller contact calls, requests to message the seller, hashtags, greetings, markdown "
            + "headings, bullet points, or any price that was not provided by the seller.";

    private final ChatMessageRepository chatMessageRepository;
    private final AssistantModelClient assistantModelClient;
    private final IntentClassifier intentClassifier;
    private final OrderClient orderClient;
    private final PolicySearchService policySearchService;
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

        IntentClassification classification = intentClassifier.classify(normalizedMessage);
        AssistantPromptContext promptContext = resolvePromptContext(
                classification,
                normalizedMessage,
                normalizedUserId,
                traceId
        );
        log.info("Resolved assistant context for intent {} and user {} with traceId {}",
                promptContext.intent(), normalizedUserId, traceId);

        List<AssistantChatMessage> promptMessages = buildPromptMessages(
                history,
                normalizedMessage,
                normalizedUserId,
                promptContext
        );
        long aiStartedAt = System.nanoTime();
        String assistantReply = assistantModelClient.generateReply(promptMessages);
        long aiDurationMs = elapsedMillis(aiStartedAt);
        assistantReply = applyRealtimeUnavailableNotice(assistantReply, promptContext);

        saveConversationTurn(normalizedUserId, normalizedMessage, assistantReply);

        log.info("Completed assistant chat request for user {} with traceId {} in {} ms. AI response time was {} ms",
                normalizedUserId, traceId, elapsedMillis(startedAt), aiDurationMs);
        return assistantReply;
    }

    @Override
    public List<ChatMessageResponseDTO> getHistory(String userId, int limit) {
        if (!StringUtils.hasText(userId)) {
            throw new InvalidInteractionRequestException("userId is required");
        }

        String normalizedUserId = userId.trim();
        int normalizedLimit = Math.min(50, Math.max(1, limit));
        List<ChatMessage> history = new ArrayList<>(chatMessageRepository.findAssistantConversationHistory(
                normalizedUserId,
                assistantProperties.assistantId(),
                PageRequest.of(0, normalizedLimit)
        ));
        Collections.reverse(history);

        return history.stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public void clearHistory(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new InvalidInteractionRequestException("userId is required");
        }

        String normalizedUserId = userId.trim();
        long deletedCount = chatMessageRepository.deleteAssistantConversation(
                normalizedUserId,
                assistantProperties.assistantId()
        );
        log.info("Cleared {} assistant history messages for user {}", deletedCount, normalizedUserId);
    }

    @Override
    public ProductDescriptionResponse generateProductDescription(ProductDescriptionRequest request) {
        validateProductDescriptionRequest(request);

        List<AssistantChatMessage> messages = List.of(
                new AssistantChatMessage(SYSTEM_ROLE, PRODUCT_DESCRIPTION_SYSTEM_PROMPT),
                new AssistantChatMessage(USER_ROLE, buildProductDescriptionUserPrompt(request))
        );
        String traceId = currentTraceId();

        try {
            String generatedDescription = assistantModelClient.generateReply(messages);
            log.info("Generated product description with traceId {}", traceId);
            return new ProductDescriptionResponse(generatedDescription);
        } catch (AssistantServiceUnavailableException | AssistantGatewayTimeoutException exception) {
            log.warn("Product description generation failed with traceId {}. Returning an empty description. Reason: {}",
                    traceId, exception.getClass().getSimpleName());
            return new ProductDescriptionResponse("");
        }
    }

    private List<AssistantChatMessage> buildPromptMessages(
            List<ChatMessage> history,
            String currentMessage,
            String userId,
            AssistantPromptContext promptContext
    ) {
        List<AssistantChatMessage> messages = new ArrayList<>();
        messages.add(new AssistantChatMessage(SYSTEM_ROLE, buildSystemPrompt(promptContext)));

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

    private AssistantPromptContext resolvePromptContext(
            IntentClassification classification,
            String message,
            String userId,
            String traceId
    ) {
        Intent intent = classification == null || classification.intent() == null
                ? Intent.GENERAL
                : classification.intent();

        return switch (intent) {
            case SYSTEM -> buildOrderPromptContext(userId, traceId);
            case POLICY -> buildPolicyPromptContext(message, traceId);
            case GENERAL -> buildProductPromptContext(message, userId, traceId);
        };
    }

    private AssistantPromptContext buildProductPromptContext(String message, String userId, String traceId) {
        AssistantProductContext productContext = productContextService.buildContext(message, userId, traceId);
        return new AssistantPromptContext(
                Intent.GENERAL,
                productContext.promptAddition(),
                productContext.realtimeUnavailable()
        );
    }

    private AssistantPromptContext buildOrderPromptContext(String userId, String traceId) {
        try {
            Optional<OrderSummary> latestOrder = orderClient.findLatestOrder(userId);
            String promptAddition = latestOrder
                    .map(this::formatLatestOrderContext)
                    .orElse("H-Smart system context: The current user has no recent orders. "
                            + "Answer naturally and tell the user there is no order data available yet.");
            return new AssistantPromptContext(Intent.SYSTEM, promptAddition, false);
        } catch (RuntimeException exception) {
            log.warn("Order system context lookup failed for user {} with traceId {}. Reason: {}",
                    userId, traceId, exception.getClass().getSimpleName());
            return new AssistantPromptContext(
                    Intent.SYSTEM,
                    "H-Smart system context: Order data is currently unavailable. "
                            + "Tell the user to try again later without inventing order details.",
                    false
            );
        }
    }

    private AssistantPromptContext buildPolicyPromptContext(String message, String traceId) {
        try {
            List<String> policyChunks = policySearchService.findRelevantPolicyChunks(message);
            String promptAddition = policyChunks.isEmpty()
                    ? "H-Smart policy context: No matching policy entries were found in hsmart-policy-index. "
                    + "Answer with general platform guidance and avoid inventing official rules."
                    : formatPolicyContext(policyChunks);
            log.info("Loaded {} policy chunks for assistant request with traceId {}",
                    policyChunks.size(), traceId);
            return new AssistantPromptContext(Intent.POLICY, promptAddition, false);
        } catch (RuntimeException exception) {
            log.warn("Policy search context lookup failed with traceId {}. Reason: {}",
                    traceId, exception.getClass().getSimpleName());
            return new AssistantPromptContext(
                    Intent.POLICY,
                    "H-Smart policy context: Policy search is currently unavailable. "
                            + "Tell the user to try again later and do not invent official policy details.",
                    false
            );
        }
    }

    private String formatLatestOrderContext(OrderSummary order) {
        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(VIETNAM_LOCALE);
        String amount = order.amount() == null ? "unknown" : currencyFormatter.format(order.amount());

        return "H-Smart system context for the current user: Latest order details: "
                + "orderId=" + fallback(order.id())
                + ", productId=" + fallback(order.productId())
                + ", sellerId=" + fallback(order.sellerId())
                + ", amount=" + amount
                + ", status=" + fallback(order.status())
                + ", createdAt=" + fallback(order.createdAt())
                + ", updatedAt=" + fallback(order.updatedAt())
                + ". Use this system data to answer the user's question. Do not invent missing order details.";
    }

    private String formatPolicyContext(List<String> policyChunks) {
        StringBuilder builder = new StringBuilder(
                "H-Smart policy context from Elasticsearch index hsmart-policy-index:"
        );

        for (int index = 0; index < Math.min(2, policyChunks.size()); index++) {
            builder.append("\n")
                    .append(index + 1)
                    .append(". ")
                    .append(shortText(policyChunks.get(index)));
        }

        builder.append("\nUse these policy entries to answer the user's question. Do not invent official rules.");
        return builder.toString();
    }

    private String buildSystemPrompt(AssistantPromptContext promptContext) {
        if (promptContext == null || !promptContext.hasPromptAddition()) {
            return assistantProperties.systemPrompt();
        }

        return assistantProperties.systemPrompt() + "\n\n" + promptContext.promptAddition();
    }

    private String buildProductDescriptionUserPrompt(ProductDescriptionRequest request) {
        return "Write a safe editable product listing description for a marketplace listing from these provided details only:\n"
                + "- Product name: " + request.getProductName().trim() + "\n"
                + "- Category: " + request.getCategory().trim() + "\n"
                + "- Condition: " + request.getCondition().trim() + "\n"
                + buildProductDescriptionPriceLine(request) + "\n"
                + "Write in Vietnamese, around 45 to 60 words, with a friendly buyer-focused tone. "
                + "The description should help the seller present the item clearly and mention only that the seller "
                + "can add missing specifications before publishing. Do not ask buyers to contact or message anyone. "
                + "If the condition is only 'Used', treat it as previously owned only; do not say the item works well, "
                + "looks good, or is in good condition unless that exact detail was provided. "
                + "Important constraints: the image classifier only suggests the product category. It does not "
                + "verify brand, model, size, working condition, sound quality, visual quality, accessories, "
                + "warranty, defects, or delivery details. If a detail is unknown, phrase it as something the seller "
                + "should review or add before publishing, not as a confirmed fact.";
    }

    private String buildProductDescriptionPriceLine(ProductDescriptionRequest request) {
        if (request.getPrice().signum() == 0) {
            return "- Price: not provided by the seller yet. Do not mention price in the description.";
        }
        return "- Price: " + request.getPrice().stripTrailingZeros().toPlainString() + " VND";
    }

    private void validateProductDescriptionRequest(ProductDescriptionRequest request) {
        if (request == null) {
            throw new InvalidInteractionRequestException("product description request is required");
        }
        if (!StringUtils.hasText(request.getProductName())) {
            throw new InvalidInteractionRequestException("productName is required");
        }
        if (!StringUtils.hasText(request.getCategory())) {
            throw new InvalidInteractionRequestException("category is required");
        }
        if (!StringUtils.hasText(request.getCondition())) {
            throw new InvalidInteractionRequestException("condition is required");
        }
        if (request.getPrice() == null || request.getPrice().signum() < 0) {
            throw new InvalidInteractionRequestException("price must be zero or greater");
        }
    }

    private String applyRealtimeUnavailableNotice(String assistantReply, AssistantPromptContext promptContext) {
        if (promptContext == null || !promptContext.realtimeUnavailable()) {
            return assistantReply;
        }

        String requiredNotice = ProductContextServiceImpl.REALTIME_UNAVAILABLE_NOTICE;
        if (assistantReply != null && assistantReply.contains("không thể truy cập dữ liệu thời gian thực")) {
            return assistantReply;
        }

        return requiredNotice + "\n\n" + assistantReply;
    }

    private String shortText(String value) {
        if (!StringUtils.hasText(value)) {
            return "No content is available";
        }

        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 240) {
            return normalized;
        }
        return normalized.substring(0, 237) + "...";
    }

    private String fallback(Object value) {
        if (value == null || !StringUtils.hasText(value.toString())) {
            return "unknown";
        }
        return value.toString().trim();
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

    private ChatMessageResponseDTO toResponse(ChatMessage message) {
        return ChatMessageResponseDTO.builder()
                .id(message.getId())
                .senderId(message.getSenderId())
                .receiverId(message.getReceiverId())
                .productId(message.getProductId())
                .content(message.getContent())
                .timestamp(message.getTimestamp())
                .build();
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
