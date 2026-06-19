package com.hsmart.backend.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant")
public record AssistantProperties(
        String providerUrl,
        String apiKey,
        String model,
        int connectTimeoutMs,
        int readTimeoutMs,
        int historyLimit,
        String assistantId,
        String systemPrompt,
        double temperature,
        int maxTokens,
        double frequencyPenalty,
        double productDescriptionTemperature,
        int productDescriptionMaxTokens
) {
}
