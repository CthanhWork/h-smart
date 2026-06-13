package com.hsmart.backend.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.location.catalog")
public record LocationCatalogProperties(
        String baseUrl,
        int connectTimeoutMs,
        int readTimeoutMs
) {
}
