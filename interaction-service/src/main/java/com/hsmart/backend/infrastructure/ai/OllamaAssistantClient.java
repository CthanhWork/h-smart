package com.hsmart.backend.infrastructure.ai;

import com.hsmart.backend.application.dto.AssistantChatMessage;
import com.hsmart.backend.application.exceptions.AssistantServiceUnavailableException;
import com.hsmart.backend.infrastructure.config.AssistantProperties;
import com.hsmart.backend.service.AssistantModelClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaAssistantClient implements AssistantModelClient {

    @Qualifier("ollamaRestClient")
    private final RestClient ollamaRestClient;
    private final AssistantProperties assistantProperties;

    @Override
    public String generateReply(List<AssistantChatMessage> messages) {
        OllamaChatRequest request = new OllamaChatRequest(
                assistantProperties.model(),
                messages,
                false
        );

        try {
            OllamaChatResponse response = ollamaRestClient.post()
                    .uri("/api/chat")
                    .body(request)
                    .retrieve()
                    .body(OllamaChatResponse.class);

            if (response == null || response.message() == null
                    || !StringUtils.hasText(response.message().content())) {
                throw new AssistantServiceUnavailableException("Assistant response is empty");
            }

            return response.message().content().trim();
        } catch (RestClientException exception) {
            log.error("Ollama request failed for model {}", assistantProperties.model(), exception);
            throw new AssistantServiceUnavailableException("Assistant service is unavailable", exception);
        }
    }

    private record OllamaChatRequest(
            String model,
            List<AssistantChatMessage> messages,
            boolean stream
    ) {
    }

    private record OllamaChatResponse(
            AssistantChatMessage message,
            boolean done
    ) {
    }
}
