package com.hsmart.backend.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.backend.application.dto.AssistantChatMessage;
import com.hsmart.backend.application.exceptions.AssistantGatewayTimeoutException;
import com.hsmart.backend.application.exceptions.AssistantServiceUnavailableException;
import com.hsmart.backend.infrastructure.config.AssistantProperties;
import com.hsmart.backend.service.AssistantModelClient;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CloudAssistantClient implements AssistantModelClient {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    @Qualifier("aiProviderRestClient")
    private final RestClient aiProviderRestClient;
    private final AssistantProperties assistantProperties;
    private final ObjectMapper objectMapper;

    @Override
    public String generateReply(List<AssistantChatMessage> messages) {
        return sendRequest(messages,
                assistantProperties.temperature(),
                assistantProperties.maxTokens(),
                assistantProperties.frequencyPenalty());
    }

    @Override
    public String generateDescriptionReply(List<AssistantChatMessage> messages) {
        return sendRequest(messages,
                assistantProperties.productDescriptionTemperature(),
                assistantProperties.productDescriptionMaxTokens(),
                null);
    }

    @Override
    public void generateStreamingReply(List<AssistantChatMessage> messages,
                                       Consumer<String> onToken, Runnable onComplete) {
        validateProviderConfiguration();

        ChatCompletionRequest request = buildRequest(
                assistantProperties.model(),
                messages,
                true,
                assistantProperties.temperature(),
                assistantProperties.maxTokens(),
                assistantProperties.frequencyPenalty()
        );

        executeStreamingRequest(request, onToken, onComplete, request.frequencyPenalty() != null);
    }

    private String sendRequest(List<AssistantChatMessage> messages,
                               double temperature, int maxTokens, Double frequencyPenalty) {
        validateProviderConfiguration();

        ChatCompletionRequest request = buildRequest(
                assistantProperties.model(),
                messages,
                false,
                temperature,
                maxTokens,
                frequencyPenalty
        );

        return executeChatCompletionRequest(request, request.frequencyPenalty() != null);
    }

    private void executeStreamingRequest(ChatCompletionRequest request,
                                         Consumer<String> onToken,
                                         Runnable onComplete,
                                         boolean canRetryWithoutFrequencyPenalty) {
        try {
            aiProviderRestClient.post()
                    .uri(CHAT_COMPLETIONS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + assistantProperties.apiKey().trim())
                    .body(request)
                    .exchange((req, response) -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.startsWith("data: ")) {
                                    continue;
                                }
                                String data = line.substring(6).trim();
                                if ("[DONE]".equals(data)) {
                                    break;
                                }
                                String token = extractStreamingToken(data);
                                if (token != null && !token.isEmpty()) {
                                    try {
                                        onToken.accept(token);
                                    } catch (RuntimeException ex) {
                                        log.warn("Streaming token delivery failed, aborting stream. Reason: {}",
                                                ex.getMessage());
                                        break;
                                    }
                                }
                            }
                        }
                        onComplete.run();
                        return null;
                    });
        } catch (RestClientResponseException exception) {
            if (canRetryWithoutFrequencyPenalty && isUnsupportedFrequencyPenalty(exception)) {
                log.warn("AI provider rejected frequency_penalty for model {} during streaming. Retrying without that field",
                        assistantProperties.model());
                executeStreamingRequest(withoutFrequencyPenalty(request), onToken, onComplete, false);
                return;
            }
            log.error("Streaming request failed with HTTP {} for model {}",
                    exception.getStatusCode().value(), assistantProperties.model(), exception);
            throw new AssistantServiceUnavailableException("Streaming assistant is unavailable", exception);
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                log.error("Streaming request timed out for model {}", assistantProperties.model(), exception);
                throw new AssistantGatewayTimeoutException("Streaming assistant timed out", exception);
            }
            log.error("Streaming network request failed for model {}", assistantProperties.model(), exception);
            throw new AssistantServiceUnavailableException("Streaming assistant is unavailable", exception);
        } catch (RestClientException exception) {
            log.error("Streaming request failed for model {}", assistantProperties.model(), exception);
            throw new AssistantServiceUnavailableException("Streaming assistant is unavailable", exception);
        }
    }

    private String executeChatCompletionRequest(ChatCompletionRequest request, boolean canRetryWithoutFrequencyPenalty) {
        try {
            ChatCompletionResponse response = aiProviderRestClient.post()
                    .uri(CHAT_COMPLETIONS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + assistantProperties.apiKey().trim())
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            AssistantChatMessage message = extractAssistantMessage(response);
            if (message == null || !StringUtils.hasText(message.content())) {
                throw new AssistantServiceUnavailableException("Assistant response is empty");
            }

            return message.content().trim();
        } catch (RestClientResponseException exception) {
            if (canRetryWithoutFrequencyPenalty && isUnsupportedFrequencyPenalty(exception)) {
                log.warn("AI provider rejected frequency_penalty for model {}. Retrying without that field",
                        assistantProperties.model());
                return executeChatCompletionRequest(withoutFrequencyPenalty(request), false);
            }
            log.error("AI provider returned HTTP {} for model {}",
                    exception.getStatusCode().value(), assistantProperties.model(), exception);
            if (exception.getStatusCode().value() == 408 || exception.getStatusCode().value() == 504) {
                throw new AssistantGatewayTimeoutException("Assistant service timed out", exception);
            }
            throw new AssistantServiceUnavailableException("Assistant service is unavailable", exception);
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                log.error("AI provider request timed out for model {}", assistantProperties.model(), exception);
                throw new AssistantGatewayTimeoutException("Assistant service timed out", exception);
            }
            log.error("AI provider network request failed for model {}", assistantProperties.model(), exception);
            throw new AssistantServiceUnavailableException("Assistant service is unavailable", exception);
        } catch (RestClientException exception) {
            log.error("AI provider request failed for model {}", assistantProperties.model(), exception);
            throw new AssistantServiceUnavailableException("Assistant service is unavailable", exception);
        }
    }

    private ChatCompletionRequest buildRequest(String model,
                                               List<AssistantChatMessage> messages,
                                               boolean stream,
                                               double temperature,
                                               int maxTokens,
                                               Double frequencyPenalty) {
        return new ChatCompletionRequest(
                model,
                messages,
                stream,
                temperature,
                maxTokens,
                frequencyPenalty
        );
    }

    private ChatCompletionRequest withoutFrequencyPenalty(ChatCompletionRequest request) {
        return buildRequest(
                request.model(),
                request.messages(),
                request.stream(),
                request.temperature(),
                request.maxTokens(),
                null
        );
    }

    private String extractStreamingToken(String jsonChunk) {
        try {
            StreamingChunk chunk = objectMapper.readValue(jsonChunk, StreamingChunk.class);
            if (chunk.choices() == null || chunk.choices().isEmpty()) {
                return null;
            }
            StreamingDelta delta = chunk.choices().get(0).delta();
            return delta != null ? delta.content() : null;
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private void validateProviderConfiguration() {
        if (!StringUtils.hasText(assistantProperties.apiKey())) {
            throw new AssistantServiceUnavailableException("Assistant API key is not configured");
        }
        if (!StringUtils.hasText(assistantProperties.model())) {
            throw new AssistantServiceUnavailableException("Assistant model is not configured");
        }
    }

    private AssistantChatMessage extractAssistantMessage(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return null;
        }
        ChatCompletionChoice firstChoice = response.choices().get(0);
        return firstChoice == null ? null : firstChoice.message();
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String className = current.getClass().getName().toLowerCase();
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
            if (className.contains("timeout") || message.contains("timed out") || message.contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isUnsupportedFrequencyPenalty(RestClientResponseException exception) {
        if (exception.getStatusCode().value() != 400) {
            return false;
        }

        String responseBody = exception.getResponseBodyAsString();
        if (!StringUtils.hasText(responseBody)) {
            return false;
        }

        String normalizedBody = responseBody.toLowerCase();
        return normalizedBody.contains("frequency_penalty")
                && (normalizedBody.contains("unknown name") || normalizedBody.contains("cannot find field"));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ChatCompletionRequest(
            String model,
            List<AssistantChatMessage> messages,
            boolean stream,
            Double temperature,
            @JsonProperty("max_tokens") Integer maxTokens,
            @JsonProperty("frequency_penalty") Double frequencyPenalty
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

    private record StreamingChunk(
            List<StreamingChoice> choices
    ) {
    }

    private record StreamingChoice(
            StreamingDelta delta
    ) {
    }

    private record StreamingDelta(
            String content
    ) {
    }
}
