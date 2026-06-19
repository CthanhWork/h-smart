package com.hsmart.backend.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hsmart.backend.application.dto.AssistantChatMessage;
import com.hsmart.backend.application.exceptions.AssistantGatewayTimeoutException;
import com.hsmart.backend.application.exceptions.AssistantServiceUnavailableException;
import com.hsmart.backend.infrastructure.config.AssistantProperties;
import com.hsmart.backend.service.AssistantModelClient;
import java.util.List;
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

    private String sendRequest(List<AssistantChatMessage> messages,
                               double temperature, int maxTokens, Double frequencyPenalty) {
        validateProviderConfiguration();

        ChatCompletionRequest request = new ChatCompletionRequest(
                assistantProperties.model(),
                messages,
                false,
                temperature,
                maxTokens,
                frequencyPenalty
        );

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
}
