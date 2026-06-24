package com.hsmart.order.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment-service")
public record PaymentServiceProperties(
        String baseUrl,
        int connectTimeoutMs,
        int readTimeoutMs
) {
}
