package com.hsmart.backend.service.impl;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.hsmart.backend.application.dto.PolicySearchResult;
import com.hsmart.backend.infrastructure.config.PolicySearchProperties;
import com.hsmart.backend.service.PolicySearchClient;
import com.hsmart.backend.service.PolicySearchService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicySearchServiceImpl implements PolicySearchService {

    private final PolicySearchClient policySearchClient;
    private final PolicySearchProperties policySearchProperties;
    private final Tracer tracer;

    @Override
    public List<String> findRelevantPolicyChunks(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        String normalizedQuery = query.trim();
        String traceId = currentTraceId();
        long startedAt = System.nanoTime();

        List<String> chunks = policySearchClient.searchPolicies(normalizedQuery).stream()
                .sorted(Comparator.comparingDouble(PolicySearchResult::score).reversed())
                .filter(result -> result.score() >= Math.max(0.0, policySearchProperties.minScore()))
                .limit(Math.max(1, policySearchProperties.maxChunks()))
                .map(this::toChunk)
                .toList();

        long durationMs = elapsedMillis(startedAt);
        log.info("Policy search completed",
                kv("traceId", traceId),
                kv("searchDurationMs", durationMs),
                kv("index", policySearchProperties.indexName()),
                kv("chunkCount", chunks.size())
        );

        return chunks;
    }

    private String toChunk(PolicySearchResult result) {
        String title = fallback(result.title(), "Untitled policy");
        String content = fallback(result.content(), "No policy content is available");
        return "Title: " + title + "\nContent: " + content;
    }

    private String fallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
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
