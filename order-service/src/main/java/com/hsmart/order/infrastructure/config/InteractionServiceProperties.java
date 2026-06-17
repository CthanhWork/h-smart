package com.hsmart.order.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "interaction-service")
public record InteractionServiceProperties(
        String baseUrl,
        Integer connectTimeoutMs,
        Integer readTimeoutMs
) {
    public InteractionServiceProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://interaction-service" : baseUrl;
        connectTimeoutMs = connectTimeoutMs == null ? 2000 : connectTimeoutMs;
        readTimeoutMs = readTimeoutMs == null ? 3000 : readTimeoutMs;
    }
}
