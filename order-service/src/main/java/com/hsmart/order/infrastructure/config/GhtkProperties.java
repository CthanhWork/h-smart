package com.hsmart.order.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ghtk")
public record GhtkProperties(
        String apiUrl,
        String apiToken,
        int connectTimeoutMs,
        int readTimeoutMs,
        int defaultWeightGrams,
        String clientSource,
        String webhookHash
) {
}
