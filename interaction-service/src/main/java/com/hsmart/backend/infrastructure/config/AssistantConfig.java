package com.hsmart.backend.infrastructure.config;

import java.time.Duration;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
public class AssistantConfig {

    @Bean
    @Qualifier("streamingExecutor")
    public Executor streamingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("assistant-stream-");
        executor.initialize();
        return executor;
    }

    @Bean
    @Qualifier("aiProviderRestClient")
    public RestClient aiProviderRestClient(AssistantProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(resolveTimeout(properties.connectTimeoutMs(), 2_000)))
                .withReadTimeout(Duration.ofMillis(resolveTimeout(properties.readTimeoutMs(), 60_000)));

        return RestClient.builder()
                .baseUrl(properties.providerUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }

    @Bean
    @Qualifier("intentClassifierRestClient")
    public RestClient intentClassifierRestClient(
            AssistantProperties assistantProperties,
            IntentClassifierProperties classifierProperties
    ) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(resolveTimeout(classifierProperties.connectTimeoutMs(), 1_000)))
                .withReadTimeout(Duration.ofMillis(resolveTimeout(classifierProperties.readTimeoutMs(), 5_000)));

        return RestClient.builder()
                .baseUrl(assistantProperties.providerUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }

    private long resolveTimeout(int configuredTimeoutMs, int fallbackTimeoutMs) {
        return configuredTimeoutMs > 0 ? configuredTimeoutMs : fallbackTimeoutMs;
    }
}
