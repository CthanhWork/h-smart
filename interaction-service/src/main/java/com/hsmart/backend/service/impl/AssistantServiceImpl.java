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
import com.hsmart.backend.service.MarketplaceScopeGuard;
import com.hsmart.backend.service.OrderClient;
import com.hsmart.backend.service.PolicySearchService;
import com.hsmart.backend.service.ProductContextService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.io.IOException;
import java.time.Duration;
import java.io.UncheckedIOException;
import java.text.NumberFormat;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantServiceImpl implements AssistantService {

    private static final String SYSTEM_ROLE = "system";
    private static final String USER_ROLE = "user";
    private static final String ASSISTANT_ROLE = "assistant";
    private static final Locale VIETNAM_LOCALE = Locale.forLanguageTag("vi-VN");
    private static final List<String> EDITORIAL_PHRASES = List.of(
            "vui lòng",
            "hãy kiểm tra",
            "hãy bổ sung",
            "cần bổ sung",
            "nên bổ sung",
            "trước khi đăng",
            "trước khi đăng tin",
            "thông tin còn thiếu",
            "chưa được cung cấp",
            "người bán nên",
            "bạn nên",
            "bạn có thể thêm",
            "bổ sung thêm"
    );

    private static final String PRODUCT_DESCRIPTION_SYSTEM_PROMPT =
            "Bạn là copywriter của H-Smart — sàn C2C chuyên đồ gia dụng đã qua sử dụng tại Việt Nam. "
            + "Nhiệm vụ: viết đoạn mô tả sản phẩm bằng tiếng Việt dựa hoàn toàn vào thông tin được cung cấp. "
            + "Yêu cầu đầu ra: một đoạn văn khoảng 45–70 từ, giọng thân thiện hướng đến người mua, sẵn sàng đăng ngay. "
            + "Quy tắc bắt buộc: "
            + "(1) Chỉ dùng thông tin đã cung cấp — không bịa thương hiệu, model, kích thước, màu sắc, tình trạng hoạt động, phụ kiện, bảo hành hay khuyết điểm. "
            + "(2) Không yêu cầu bổ sung thông tin, không đặt câu hỏi, không dùng placeholder như [tên thương hiệu]. "
            + "(3) Chi tiết chưa biết: bỏ qua hoàn toàn, không đề cập là 'chưa cung cấp' hay 'chưa rõ'. "
            + "(4) Không dùng hashtag, markdown, lời chào, tiêu đề, gạch đầu dòng hay thông tin liên hệ. "
            + "Lưu ý hệ thống: tên sản phẩm do computer vision nhận diện — chỉ xác định được danh mục, "
            + "không xác nhận thương hiệu, model hay tình trạng thực tế.";

    private static final List<String> PLATFORM_SELLER_PHRASES = List.of(
            "h-smart",
            "chúng tôi",
            "cửa hàng",
            "shop"
    );
    private static final String SELLER_PERSPECTIVE_PRODUCT_DESCRIPTION_SYSTEM_PROMPT =
            "Bạn là copywriter của H-Smart, đang hỗ trợ người bán đăng tin trên H-Smart — sàn C2C chuyên đồ gia dụng đã qua sử dụng tại Việt Nam. "
            + "Nhiệm vụ: viết đoạn mô tả sản phẩm bằng tiếng Việt dựa hoàn toàn vào thông tin được cung cấp, theo góc nhìn của người bán đang giới thiệu món đồ của mình. "
            + "Yêu cầu đầu ra: một đoạn văn khoảng 45–70 từ, giọng tự nhiên, thân thiện, hướng đến người mua và sẵn sàng đăng ngay. "
            + "Quy tắc bắt buộc: "
            + "(1) Chỉ dùng thông tin đã cung cấp — không bịa thương hiệu, model, kích thước, màu sắc, tình trạng hoạt động, phụ kiện, bảo hành hay khuyết điểm. "
            + "(2) Không yêu cầu bổ sung thông tin, không đặt câu hỏi, không dùng placeholder như [tên thương hiệu]. "
            + "(3) Chi tiết chưa biết: bỏ qua hoàn toàn, không đề cập là 'chưa cung cấp' hay 'chưa rõ'. "
            + "(4) Không được viết như thể H-Smart, cửa hàng, shop hay 'chúng tôi' đang là người bán; không kêu gọi 'ghé H-Smart', 'mua tại H-Smart' hoặc nhắc nền tảng như bên rao bán. "
            + "(5) Không dùng hashtag, markdown, lời chào, tiêu đề, gạch đầu dòng hay thông tin liên hệ. "
            + "Lưu ý hệ thống: tên sản phẩm do computer vision nhận diện — chỉ xác định được danh mục, "
            + "không xác nhận thương hiệu, model hay tình trạng thực tế.";

    private final ChatMessageRepository chatMessageRepository;
    private final AssistantModelClient assistantModelClient;
    private final IntentClassifier intentClassifier;
    private final OrderClient orderClient;
    private final PolicySearchService policySearchService;
    private final ProductContextService productContextService;
    private final MarketplaceScopeGuard marketplaceScopeGuard;
    private final AssistantProperties assistantProperties;
    private final Tracer tracer;

    @Autowired
    @Qualifier("streamingExecutor")
    private Executor streamingExecutor;

    @Value("${assistant.history-retention-days:90}")
    private int historyRetentionDays;

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

        if (!marketplaceScopeGuard.isInScope(normalizedMessage, history)) {
            log.info("Rejected out-of-scope assistant request for user {} with traceId {}",
                    normalizedUserId, traceId);
            String outOfScopeReply = MarketplaceScopeGuard.OUT_OF_SCOPE_REPLY;
            saveConversationTurn(normalizedUserId, normalizedMessage, outOfScopeReply);
            return outOfScopeReply;
        }

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
    public SseEmitter streamChat(String userId, String message) {
        if (!StringUtils.hasText(userId)) {
            throw new InvalidInteractionRequestException("userId is required");
        }
        if (!StringUtils.hasText(message)) {
            throw new InvalidInteractionRequestException("message is required");
        }

        String normalizedUserId = userId.trim();
        String normalizedMessage = message.trim();
        String traceId = currentTraceId();
        long streamingTimeoutMs = (long) assistantProperties.readTimeoutMs() * 2;
        SseEmitter emitter = new SseEmitter(streamingTimeoutMs);

        CompletableFuture.runAsync(() -> {
            try {
                List<ChatMessage> history = new ArrayList<>(chatMessageRepository.findAssistantConversationHistory(
                        normalizedUserId,
                        assistantProperties.assistantId(),
                        PageRequest.of(0, resolveHistoryLimit())
                ));
                Collections.reverse(history);

                if (!marketplaceScopeGuard.isInScope(normalizedMessage, history)) {
                    String outOfScopeReply = MarketplaceScopeGuard.OUT_OF_SCOPE_REPLY;
                    emitter.send(SseEmitter.event().data(outOfScopeReply));
                    saveConversationTurn(normalizedUserId, normalizedMessage, outOfScopeReply);
                    emitter.complete();
                    return;
                }

                IntentClassification classification = intentClassifier.classify(normalizedMessage);
                AssistantPromptContext promptContext = resolvePromptContext(
                        classification, normalizedMessage, normalizedUserId, traceId);
                List<AssistantChatMessage> promptMessages = buildPromptMessages(
                        history, normalizedMessage, normalizedUserId, promptContext);

                AtomicBoolean emitterClosed = new AtomicBoolean(false);
                StringBuilder fullReply = new StringBuilder();

                assistantModelClient.generateStreamingReply(promptMessages, token -> {
                    if (emitterClosed.get()) {
                        return;
                    }
                    fullReply.append(token);
                    try {
                        emitter.send(SseEmitter.event().data(token));
                    } catch (IOException ex) {
                        emitterClosed.set(true);
                        throw new UncheckedIOException(ex);
                    }
                }, () -> {
                    String reply = applyRealtimeUnavailableNotice(fullReply.toString(), promptContext);
                    saveConversationTurn(normalizedUserId, normalizedMessage, reply);
                    if (!emitterClosed.get()) {
                        emitter.complete();
                    }
                });
            } catch (UncheckedIOException ignored) {
                // emitter already closed
            } catch (Exception ex) {
                log.error("Streaming assistant chat failed for user {} with traceId {}",
                        normalizedUserId, traceId, ex);
                emitter.completeWithError(ex);
            }
        }, streamingExecutor);

        return emitter;
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

    @Scheduled(cron = "${assistant.history-cleanup-cron:0 0 3 * * *}")
    public void cleanupOldAssistantHistory() {
        if (historyRetentionDays <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(historyRetentionDays));
        long deleted = chatMessageRepository.deleteOldAssistantMessages(
                assistantProperties.assistantId(), cutoff);
        log.info("Cleaned up {} assistant messages older than {} days", deleted, historyRetentionDays);
    }

    @Override
    public ProductDescriptionResponse generateProductDescription(ProductDescriptionRequest request) {
        validateProductDescriptionRequest(request);

        String userPrompt = buildRelaxedSellerPerspectiveProductDescriptionUserPrompt(request);
        List<AssistantChatMessage> messages = List.of(
                new AssistantChatMessage(SYSTEM_ROLE, buildRelaxedSellerPerspectiveSystemPrompt()),
                new AssistantChatMessage(USER_ROLE, userPrompt)
        );
        String traceId = currentTraceId();

        try {
            String raw = assistantModelClient.generateDescriptionReply(messages);
            if (containsEditorialPhrases(raw) || containsPlatformSellerPhrases(raw)) {
                log.warn("First product description attempt contained editorial phrases, retrying. traceId={}", traceId);
                List<AssistantChatMessage> retryMessages = List.of(
                        new AssistantChatMessage(SYSTEM_ROLE, buildRelaxedSellerPerspectiveSystemPrompt()),
                        new AssistantChatMessage(USER_ROLE, userPrompt
                                + "\nQuan trọng: chỉ dùng đúng dữ liệu đã có, ưu tiên các chi tiết trong ghi chú của người bán nếu có. "
                                + "Trả về ngay 1 đoạn mô tả hoàn chỉnh, không hỏi thêm.")
                );
                try {
                    raw = assistantModelClient.generateDescriptionReply(retryMessages);
                } catch (AssistantServiceUnavailableException | AssistantGatewayTimeoutException retryEx) {
                    log.warn("Retry also failed, using fallback. traceId={}", traceId);
                    return new ProductDescriptionResponse(buildPublishReadyFallback(request));
                }
            }
            String generatedDescription = toPublishReadyDescription(raw, request);
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

        for (int index = 0; index < policyChunks.size(); index++) {
            builder.append("\n")
                    .append(index + 1)
                    .append(". ")
                    .append(shortText(policyChunks.get(index)));
        }

        builder.append("\nDùng các điều khoản trên để trả lời. Không bịa quy định ngoài context.");
        return builder.toString();
    }

    private String buildSystemPrompt(AssistantPromptContext promptContext) {
        if (promptContext == null || !promptContext.hasPromptAddition()) {
            return assistantProperties.systemPrompt();
        }

        return assistantProperties.systemPrompt()
                + "\n\n### CONTEXT\n"
                + promptContext.promptAddition()
                + "\n### END CONTEXT";
    }

    private String buildProductDescriptionUserPrompt(ProductDescriptionRequest request) {
        StringBuilder sb = new StringBuilder("Viết mô tả sản phẩm sẵn sàng đăng từ thông tin dưới đây:\n")
                .append("- Tên sản phẩm: ").append(request.getProductName().trim()).append("\n")
                .append("- Danh mục: ").append(request.getCategory().trim()).append("\n")
                .append("- Tình trạng: ").append(request.getCondition().trim()).append("\n")
                .append(buildProductDescriptionPriceLine(request));

        if (StringUtils.hasText(request.getSellerNotes())) {
            sb.append("\n- Ghi chú của người bán: ").append(request.getSellerNotes().trim());
        }

        sb.append("\nViết bằng tiếng Việt, khoảng 45–70 từ, giọng thân thiện hướng đến người mua. "
                + "Chỉ trả về đoạn văn cuối cùng, không kèm lời giải thích hay yêu cầu thêm thông tin.");
        return sb.toString();
    }

    private String buildSellerPerspectiveProductDescriptionUserPrompt(ProductDescriptionRequest request) {
        StringBuilder sb = new StringBuilder("Viết mô tả sản phẩm sẵn sàng đăng từ thông tin dưới đây:\n")
                .append("- Tên sản phẩm: ").append(request.getProductName().trim()).append("\n")
                .append("- Danh mục: ").append(request.getCategory().trim()).append("\n")
                .append("- Tình trạng: ").append(request.getCondition().trim()).append("\n")
                .append(buildProductDescriptionPriceLine(request));

        if (StringUtils.hasText(request.getSellerNotes())) {
            sb.append("\n- Ghi chú của người bán: ").append(request.getSellerNotes().trim());
        }

        sb.append("\nViết bằng tiếng Việt, khoảng 45–70 từ, theo góc nhìn của người bán đang giới thiệu sản phẩm của mình cho người mua. "
                + "Không nhắc H-Smart, không viết như thể nền tảng, cửa hàng, shop hay 'chúng tôi' là người bán. "
                + "Chỉ trả về đoạn văn cuối cùng, không kèm lời giải thích hay yêu cầu thêm thông tin.");
        return sb.toString();
    }

    private String buildRelaxedSellerPerspectiveSystemPrompt() {
        return "Bạn hỗ trợ người bán viết mô tả tin đăng trên H-Smart. "
                + "Hãy viết 1 đoạn mô tả ngắn bằng tiếng Việt, tự nhiên, như chính người bán đang giới thiệu món đồ của mình. "
                + "Ưu tiên dùng các chi tiết người bán đã nhập, nhất là tình trạng thực tế, phụ kiện, quà tặng, nơi mua, bảo hành và ghi chú thêm. "
                + "Không bịa thông tin. Không viết như thể H-Smart là người bán. "
                + "Chỉ trả về 1 đoạn văn hoàn chỉnh, không gạch đầu dòng, không markdown, không hỏi thêm.";
    }

    private String buildRelaxedSellerPerspectiveProductDescriptionUserPrompt(ProductDescriptionRequest request) {
        StringBuilder sb = new StringBuilder("Viết mô tả sản phẩm từ dữ liệu sau:\n")
                .append("- Tên sản phẩm: ").append(request.getProductName().trim()).append("\n")
                .append("- Danh mục: ").append(request.getCategory().trim()).append("\n")
                .append("- Tình trạng: ").append(request.getCondition().trim()).append("\n")
                .append(buildProductDescriptionPriceLine(request));

        if (StringUtils.hasText(request.getSellerNotes())) {
            sb.append("\n- Ghi chú của người bán: ").append(request.getSellerNotes().trim());
        }

        sb.append("\nViết khoảng 40–80 từ. Nếu ghi chú của người bán có chi tiết cụ thể thì ưu tiên đưa vào mô tả một cách tự nhiên. "
                + "Viết theo góc nhìn người bán và không nhắc H-Smart như bên đang bán hàng.");
        return sb.toString();
    }

    private boolean containsEditorialPhrases(String text) {
        if (!StringUtils.hasText(text)) return false;
        return containsLoosePhrase(text, EDITORIAL_PHRASES);
    }

    private boolean containsPlatformSellerPhrases(String text) {
        if (!StringUtils.hasText(text)) return false;
        return containsLoosePhrase(text, List.of(
                "ghé h-smart",
                "mua tại h-smart",
                "h-smart đang bán",
                "h-smart xin giới thiệu",
                "h-smart mang đến"
        ));
    }

    private boolean containsLoosePhrase(String text, List<String> phrases) {
        String normalizedLower = normalizeVietnameseForComparison(text);
        return phrases.stream()
                .map(this::normalizeVietnameseForComparison)
                .anyMatch(normalizedLower::contains);
    }

    private String normalizeVietnameseForComparison(String value) {
        String normalized = value.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(VIETNAM_LOCALE);
        String withoutAccents = Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd');
        return withoutAccents;
    }

    private String toPublishReadyDescription(String generatedDescription, ProductDescriptionRequest request) {
        if (!StringUtils.hasText(generatedDescription)) {
            return buildPublishReadyFallback(request);
        }

        String normalized = generatedDescription.trim()
                .replaceAll("(?m)^[-*•]\\s*", "")
                .replaceAll("\\s+", " ");

        if (containsEditorialPhrases(normalized) || containsPlatformSellerPhrases(normalized)) {
            log.warn("Discarded non-publish-ready product description and used a safe fallback");
            return buildPublishReadyFallback(request);
        }
        return normalized;
    }

    private String buildPublishReadyFallback(ProductDescriptionRequest request) {
        String productName = request.getProductName().trim();
        String category = request.getCategory().trim().toLowerCase(VIETNAM_LOCALE);
        String condition = request.getCondition().trim().toLowerCase(VIETNAM_LOCALE);
        return productName + " là sản phẩm " + category + " " + condition
                + ", phù hợp cho nhu cầu sinh hoạt hằng ngày trong gia đình. "
                + "Thiết kế quen thuộc giúp sản phẩm dễ bố trí trong nhiều không gian, từ căn hộ, phòng riêng "
                + "đến khu vực sinh hoạt chung. Đây là lựa chọn thực tế cho người đang tìm sản phẩm gia dụng "
                + "phù hợp với nhu cầu sử dụng.";
    }

    private String buildProductDescriptionPriceLine(ProductDescriptionRequest request) {
        if (request.getPrice().signum() == 0) {
            return "- Giá: chưa được người bán cung cấp. Không đề cập giá trong mô tả.";
        }
        String formatted = NumberFormat.getNumberInstance(VIETNAM_LOCALE)
                .format(request.getPrice().longValue()) + " ₫";
        return "- Giá: " + formatted;
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
        if (assistantReply != null && assistantReply.startsWith(requiredNotice)) {
            return assistantReply;
        }

        return requiredNotice + "\n\n" + assistantReply;
    }

    private String shortText(String value) {
        if (!StringUtils.hasText(value)) {
            return "No content is available";
        }

        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 700) {
            return normalized;
        }
        return normalized.substring(0, 697) + "...";
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
