package com.hsmart.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.backend.application.dto.AssistantChatMessage;
import com.hsmart.backend.application.dto.IntentClassification;
import com.hsmart.backend.application.dto.IntentClassification.Intent;
import com.hsmart.backend.infrastructure.config.AssistantProperties;
import com.hsmart.backend.infrastructure.config.IntentClassifierProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntentClassifier {

    public static final String INTENT_CLASSIFIER_CIRCUIT_BREAKER = "intentClassifierCircuitBreaker";

    private static final String SYSTEM_ROLE = "system";
    private static final String USER_ROLE = "user";
    private static final String SYSTEM_PROMPT = """
            You are the strict intent classifier for H-Smart.
            You classify only H-Smart buying and selling assistant requests.
            Return only valid JSON. Do not return Markdown, code fences, XML, explanations, or extra text.
            The JSON format must be exactly: {"intent":"SYSTEM|POLICY|GENERAL","reason":"..."}.
            The reason must be concise English text, maximum 120 characters.

            Intent definitions:
            SYSTEM: The user explicitly wants to look up their latest or recent H-Smart order and its current status.
            POLICY: The user asks how to use H-Smart, including account setup, email verification, password reset, listing, offers, checkout, shipping, cancellation, reviews, or marketplace rules.
            GENERAL: The user asks for product discovery, product comparison, buying or selling guidance, or any other request that does not require private system data or policy lookup.

            Important boundaries:
            - Do not choose SYSTEM for general instructions such as "how do I track an order?" or "how do offers work?"; choose POLICY.
            - Do not choose SYSTEM for private data that the assistant cannot currently retrieve, such as account verification state, trust score, or a specific offer status; choose POLICY so the assistant can explain where to check it without inventing data.
            - Choose SYSTEM only when the user asks about their own latest/recent order.

            Examples:
            User: "Làm sao đăng ký tài khoản?" -> POLICY
            User: "Tôi trả giá thế nào?" -> POLICY
            User: "Đơn hàng gần nhất của tôi đang ở đâu?" -> SYSTEM
            User: "Email của tôi xác minh chưa?" -> POLICY
            User: "Tìm giúp tôi một chiếc tủ lạnh" -> GENERAL

            If the request is unrelated to H-Smart buying or selling, choose GENERAL.
            If the request is ambiguous, choose GENERAL.
            """;
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    @Qualifier("intentClassifierRestClient")
    private final RestClient intentClassifierRestClient;
    private final AssistantProperties assistantProperties;
    private final IntentClassifierProperties intentClassifierProperties;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    public IntentClassification classify(String userQuestion) {
        if (!StringUtils.hasText(userQuestion)) {
            return IntentClassification.general("Question is empty");
        }

        String normalizedQuestion = userQuestion.trim();
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(INTENT_CLASSIFIER_CIRCUIT_BREAKER);
        tagCurrentSpan(circuitBreaker);

        try {
            IntentClassification classification = circuitBreaker.executeSupplier(
                    () -> classifyWithAiProvider(normalizedQuestion)
            );
            log.info("Classified assistant intent as {} with traceId {}",
                    classification.intent(), currentTraceId());
            return classification;
        } catch (CallNotPermittedException exception) {
            log.warn("Intent classifier circuit breaker is open with traceId {}", currentTraceId());
            return classifyWithLocalFallback(normalizedQuestion);
        } catch (RuntimeException exception) {
            log.warn("Intent classification failed with traceId {}. Reason: {}",
                    currentTraceId(), exception.getClass().getSimpleName());
            return classifyWithLocalFallback(normalizedQuestion);
        }
    }

    private IntentClassification classifyWithLocalFallback(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        if (containsAny(normalized,
                "đơn hàng gần nhất", "đơn gần nhất", "đơn của tôi", "don hang gan nhat",
                "don gan nhat", "don cua toi", "latest order", "my order")) {
            return new IntentClassification(Intent.SYSTEM, "Local fallback: latest order lookup");
        }
        if (containsAny(normalized,
                "đăng ký", "dang ky", "xác minh", "xac minh", "đăng nhập", "dang nhap",
                "mật khẩu", "mat khau", "đăng bán", "dang ban", "đăng tin", "dang tin",
                "trả giá", "tra gia", "offer", "vận chuyển", "van chuyen", "giao hàng", "giao hang",
                "huỷ đơn", "hủy đơn", "huy don", "đánh giá", "danh gia", "chính sách", "chinh sach")) {
            return new IntentClassification(Intent.POLICY, "Local fallback: platform guidance");
        }
        return IntentClassification.general("Intent classifier is unavailable");
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private IntentClassification classifyWithAiProvider(String userQuestion) {
        if (!StringUtils.hasText(assistantProperties.apiKey()) || !StringUtils.hasText(assistantProperties.model())) {
            throw new IllegalStateException("Intent classifier AI provider configuration is incomplete");
        }

        String classifierModel = StringUtils.hasText(intentClassifierProperties.model())
                ? intentClassifierProperties.model()
                : assistantProperties.model();

        ChatCompletionRequest request = new ChatCompletionRequest(
                classifierModel,
                List.of(
                        new AssistantChatMessage(SYSTEM_ROLE, SYSTEM_PROMPT),
                        new AssistantChatMessage(USER_ROLE, userQuestion)
                ),
                false,
                0
        );

        ChatCompletionResponse response;
        try {
            response = intentClassifierRestClient.post()
                    .uri(CHAT_COMPLETIONS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + assistantProperties.apiKey().trim())
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
        } catch (RestClientException exception) {
            throw new IllegalStateException("Intent classifier AI provider request failed", exception);
        }

        AssistantChatMessage message = extractAssistantMessage(response);
        if (message == null || !StringUtils.hasText(message.content())) {
            throw new IllegalStateException("Intent classifier response is empty");
        }

        return parseClassification(message.content());
    }

    private AssistantChatMessage extractAssistantMessage(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return null;
        }
        ChatCompletionChoice firstChoice = response.choices().get(0);
        return firstChoice == null ? null : firstChoice.message();
    }

    private IntentClassification parseClassification(String responseContent) {
        try {
            RawIntentClassification raw = objectMapper.readValue(
                    extractJsonObject(responseContent),
                    RawIntentClassification.class
            );
            Intent intent = Intent.valueOf(raw.intent().trim().toUpperCase(Locale.ROOT));
            String reason = StringUtils.hasText(raw.reason())
                    ? raw.reason().trim()
                    : "No reason provided";
            return new IntentClassification(intent, reason);
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException exception) {
            throw new IllegalStateException("Intent classifier returned invalid JSON", exception);
        }
    }

    private String extractJsonObject(String responseContent) {
        String trimmed = responseContent.trim();
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace < 0 || lastBrace <= firstBrace) {
            throw new IllegalStateException("Intent classifier response does not contain a JSON object");
        }
        return trimmed.substring(firstBrace, lastBrace + 1);
    }

    private void tagCurrentSpan(CircuitBreaker circuitBreaker) {
        Span span = tracer.currentSpan();
        if (span == null) {
            return;
        }

        span.tag("resilience4j.circuit_breaker.name", circuitBreaker.getName());
        span.tag("resilience4j.circuit_breaker.state", circuitBreaker.getState().name());
    }

    private String currentTraceId() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return "unavailable";
        }
        return span.context().traceId();
    }

    private record ChatCompletionRequest(
            String model,
            List<AssistantChatMessage> messages,
            boolean stream,
            double temperature
    ) {
    }

    private record ChatCompletionResponse(
            List<ChatCompletionChoice> choices
    ) {
    }

    private record ChatCompletionChoice(
            AssistantChatMessage message
    ) {
    }

    private record RawIntentClassification(
            String intent,
            String reason
    ) {
    }
}
