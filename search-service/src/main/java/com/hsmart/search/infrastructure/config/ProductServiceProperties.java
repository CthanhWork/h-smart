package com.hsmart.search.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "product-service")
public record ProductServiceProperties(
        String baseUrl,
        @DefaultValue("5000") int connectTimeoutMs,
        @DefaultValue("30000") int readTimeoutMs
) {
}
