package com.hsmart.backend.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant")
public record AssistantProperties(
        String baseUrl,
        String model,
        int historyLimit,
        String assistantId,
        String systemPrompt
) {
}
