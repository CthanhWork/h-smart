package com.hsmart.backend.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "policy-search")
public record PolicySearchProperties(
        String baseUrl,
        String indexName,
        int pageSize,
        int maxChunks,
        double minScore,
        int connectTimeoutMs,
        int readTimeoutMs
) {
}
