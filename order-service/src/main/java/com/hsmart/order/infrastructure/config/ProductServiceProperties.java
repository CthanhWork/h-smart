package com.hsmart.order.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "product-service")
public record ProductServiceProperties(
        String baseUrl,
        int connectTimeoutMs,
        int readTimeoutMs
) {
}
