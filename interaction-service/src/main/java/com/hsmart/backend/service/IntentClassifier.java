package com.hsmart.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.backend.application.dto.AssistantChatMessage;
import com.hsmart.backend.application.dto.IntentClassification;
import com.hsmart.backend.application.dto.IntentClassification.Intent;
import com.hsmart.backend.infrastructure.config.AssistantProperties;
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
            Return only valid JSON. Do not return Markdown, code fences, XML, explanations, or extra text.
            The JSON format must be exactly: {"intent":"SYSTEM|POLICY|GENERAL","reason":"..."}.
            The reason must be concise English text, maximum 120 characters.

            Intent definitions:
            SYSTEM: The user wants to look up orders, account information, profile information, trust score, review count, or other private H-Smart system data.
            POLICY: The user asks about H-Smart policies, return/refund rules, buying or selling instructions, FAQ-style guidance, or platform usage rules.
            GENERAL: The user is chatting casually or asking broad product advice that does not require private system data or policy lookup.

            If the request is ambiguous, choose GENERAL.
            """;
    private static final IntentClassification FALLBACK_CLASSIFICATION =
            IntentClassification.general("Intent classifier is unavailable");
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    @Qualifier("intentClassifierRestClient")
    private final RestClient intentClassifierRestClient;
    private final AssistantProperties assistantProperties;
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
            return FALLBACK_CLASSIFICATION;
        } catch (RuntimeException exception) {
            log.warn("Intent classification failed with traceId {}. Reason: {}",
                    currentTraceId(), exception.getClass().getSimpleName());
            return FALLBACK_CLASSIFICATION;
        }
    }

    private IntentClassification classifyWithAiProvider(String userQuestion) {
        if (!StringUtils.hasText(assistantProperties.apiKey()) || !StringUtils.hasText(assistantProperties.model())) {
            throw new IllegalStateException("Intent classifier AI provider configuration is incomplete");
        }

        ChatCompletionRequest request = new ChatCompletionRequest(
                assistantProperties.model(),
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
