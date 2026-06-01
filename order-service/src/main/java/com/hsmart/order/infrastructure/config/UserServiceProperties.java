package com.hsmart.order.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "user-service")
public record UserServiceProperties(
        String baseUrl,
        int connectTimeoutMs,
        int readTimeoutMs
) {
}
