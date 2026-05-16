package com.hsmart.backend.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant.intent-classifier")
public record IntentClassifierProperties(
        int connectTimeoutMs,
        int readTimeoutMs
) {
}
