package com.hsmart.backend.infrastructure.ai;

import com.hsmart.backend.infrastructure.config.AssistantProperties;
import com.hsmart.backend.service.EmbeddingClient;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class CloudEmbeddingClient implements EmbeddingClient {

    private static final String EMBEDDINGS_PATH = "/embeddings";

    private final RestClient aiProviderRestClient;
    private final AssistantProperties assistantProperties;

    public CloudEmbeddingClient(
            @Qualifier("aiProviderRestClient") RestClient aiProviderRestClient,
            AssistantProperties assistantProperties
    ) {
        this.aiProviderRestClient = aiProviderRestClient;
        this.assistantProperties = assistantProperties;
    }

    @Override
    public List<Double> embedText(String text) {
        if (!StringUtils.hasText(assistantProperties.embeddingModel())) {
            throw new IllegalStateException("Embedding model is not configured");
        }

        Map<String, String> request = Map.of(
                "model", assistantProperties.embeddingModel(),
                "input", text
        );

        try {
            Map<String, Object> response = aiProviderRestClient.post()
                    .uri(EMBEDDINGS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + assistantProperties.apiKey().trim())
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            return parseEmbedding(response);
        } catch (RestClientException exception) {
            log.warn("Embedding request failed for model {}", assistantProperties.embeddingModel(), exception);
            throw new IllegalStateException("Embedding request failed", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Double> parseEmbedding(Map<String, Object> response) {
        if (response == null || !(response.get("data") instanceof List<?> dataList) || dataList.isEmpty()) {
            throw new IllegalStateException("Embedding response contains no data");
        }
        if (!(dataList.get(0) instanceof Map<?, ?> firstEntry)) {
            throw new IllegalStateException("Embedding response has unexpected format");
        }
        Object embeddingValue = ((Map<String, Object>) firstEntry).get("embedding");
        if (!(embeddingValue instanceof List<?> rawEmbedding)) {
            throw new IllegalStateException("Embedding vector not found in response");
        }
        return rawEmbedding.stream()
                .map(v -> v instanceof Number n ? n.doubleValue() : Double.parseDouble(v.toString()))
                .toList();
    }
}
