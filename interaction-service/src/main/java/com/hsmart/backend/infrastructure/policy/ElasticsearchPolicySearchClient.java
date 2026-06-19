package com.hsmart.backend.infrastructure.policy;

import com.hsmart.backend.application.dto.PolicySearchResult;
import com.hsmart.backend.application.exceptions.PolicySearchUnavailableException;
import com.hsmart.backend.infrastructure.config.AssistantProperties;
import com.hsmart.backend.infrastructure.config.PolicySearchProperties;
import com.hsmart.backend.service.EmbeddingClient;
import com.hsmart.backend.service.PolicySearchClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class ElasticsearchPolicySearchClient implements PolicySearchClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient policySearchRestClient;
    private final PolicySearchProperties policySearchProperties;
    private final AssistantProperties assistantProperties;
    private final EmbeddingClient embeddingClient;
    private final String internalSharedSecret;

    public ElasticsearchPolicySearchClient(
            @Qualifier("policySearchRestClient") RestClient policySearchRestClient,
            PolicySearchProperties policySearchProperties,
            AssistantProperties assistantProperties,
            Optional<EmbeddingClient> embeddingClient,
            @Value("${internal.security.secret:}") String internalSharedSecret
    ) {
        this.policySearchRestClient = policySearchRestClient;
        this.policySearchProperties = policySearchProperties;
        this.assistantProperties = assistantProperties;
        this.embeddingClient = embeddingClient.orElse(null);
        this.internalSharedSecret = internalSharedSecret;
    }

    @Override
    public List<PolicySearchResult> searchPolicies(String query) {
        try {
            Map<String, Object> request = buildSearchRequest(query);
            Map<String, Object> response = policySearchRestClient.post()
                    .uri("/{index}/_search", policySearchProperties.indexName())
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return parseResults(response);
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("Policy search request failed for query {}", query, exception);
            throw new PolicySearchUnavailableException("Policy search is unavailable", exception);
        }
    }

    private Map<String, Object> buildSearchRequest(String query) {
        int pageSize = Math.max(1, policySearchProperties.pageSize());
        Map<String, Object> request = new HashMap<>();
        request.put("size", pageSize);
        request.put("query", Map.of(
                "multi_match", Map.of(
                        "query", query,
                        "fields", List.of("title^2", "content"),
                        "fuzziness", "AUTO"
                )
        ));

        if (assistantProperties.hybridSearchEnabled() && embeddingClient != null) {
            tryAddKnnClause(request, query, pageSize);
        }

        return request;
    }

    private void tryAddKnnClause(Map<String, Object> request, String query, int pageSize) {
        try {
            List<Double> embedding = embeddingClient.embedText(query);
            if (embedding != null && !embedding.isEmpty()) {
                request.put("knn", Map.of(
                        "field", "embedding",
                        "query_vector", embedding,
                        "k", pageSize,
                        "num_candidates", Math.max(50, pageSize * 10)
                ));
                log.debug("Hybrid knn+BM25 search enabled for policy query");
            }
        } catch (Exception exception) {
            log.warn("Embedding generation failed, falling back to BM25-only policy search. Reason: {}",
                    exception.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<PolicySearchResult> parseResults(Map<String, Object> response) {
        if (response == null || !(response.get("hits") instanceof Map<?, ?> hitsWrapper)) {
            return List.of();
        }
        Object hitsValue = hitsWrapper.get("hits");
        if (!(hitsValue instanceof List<?> hits)) {
            return List.of();
        }

        return hits.stream()
                .filter(Map.class::isInstance)
                .map(hit -> toPolicySearchResult((Map<String, Object>) hit))
                .filter(Objects::nonNull)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private PolicySearchResult toPolicySearchResult(Map<String, Object> hit) {
        if (!(hit.get("_source") instanceof Map<?, ?> source)) {
            return null;
        }

        String title = valueAsString((Map<String, Object>) source, "title");
        String content = valueAsString((Map<String, Object>) source, "content");
        String category = valueAsString((Map<String, Object>) source, "category");
        double score = hit.get("_score") instanceof Number number ? number.doubleValue() : 0.0;

        return new PolicySearchResult(
                fallback(title, "Untitled policy"),
                fallback(content, "No policy content is available"),
                fallback(category, "General"),
                score
        );
    }

    private String valueAsString(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString().trim();
            }
        }
        return "";
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
