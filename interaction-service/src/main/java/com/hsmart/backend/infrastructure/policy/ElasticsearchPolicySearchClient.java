package com.hsmart.backend.infrastructure.policy;

import com.hsmart.backend.application.dto.PolicySearchResult;
import com.hsmart.backend.application.exceptions.PolicySearchUnavailableException;
import com.hsmart.backend.infrastructure.config.PolicySearchProperties;
import com.hsmart.backend.service.PolicySearchClient;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final String internalSharedSecret;

    public ElasticsearchPolicySearchClient(
            @Qualifier("policySearchRestClient") RestClient policySearchRestClient,
            PolicySearchProperties policySearchProperties,
            @Value("${internal.security.secret:}") String internalSharedSecret
    ) {
        this.policySearchRestClient = policySearchRestClient;
        this.policySearchProperties = policySearchProperties;
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
        return Map.of(
                "size", Math.max(1, policySearchProperties.pageSize()),
                "query", Map.of(
                        "multi_match", Map.of(
                                "query", query,
                                "fields", List.of("title^2", "content"),
                                "fuzziness", "AUTO"
                        )
                )
        );
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
