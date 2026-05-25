package com.hsmart.backend.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, AiServiceProperties aiServiceProperties) {
        return builder
                .setConnectTimeout(Duration.ofMillis(resolveTimeout(aiServiceProperties.connectTimeoutMs(), 2000)))
                .setReadTimeout(Duration.ofMillis(resolveTimeout(aiServiceProperties.readTimeoutMs(), 5000)))
                .build();
    }

    private long resolveTimeout(Integer configuredTimeoutMs, long defaultTimeoutMs) {
        return configuredTimeoutMs != null && configuredTimeoutMs > 0 ? configuredTimeoutMs : defaultTimeoutMs;
    }
}
