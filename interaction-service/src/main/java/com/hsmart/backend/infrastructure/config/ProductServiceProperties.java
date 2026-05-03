package com.hsmart.backend.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "product-service")
public record ProductServiceProperties(
        String baseUrl,
        int pageSize,
        int connectTimeoutMs,
        int readTimeoutMs
) {
}
