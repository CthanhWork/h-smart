package com.hsmart.backend.infrastructure.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AssistantConfig {

    @Bean
    @Qualifier("ollamaRestClient")
    public RestClient ollamaRestClient(RestClient.Builder builder, AssistantProperties properties) {
        return builder
                .baseUrl(properties.baseUrl())
                .build();
    }
}
