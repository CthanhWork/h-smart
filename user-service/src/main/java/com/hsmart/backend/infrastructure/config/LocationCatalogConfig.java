package com.hsmart.backend.infrastructure.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(LocationCatalogProperties.class)
public class LocationCatalogConfig {

    @Bean
    @Qualifier("locationCatalogRestClient")
    public RestClient locationCatalogRestClient(LocationCatalogProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(resolveTimeout(properties.connectTimeoutMs(), 2_000)));
        requestFactory.setReadTimeout(Duration.ofMillis(resolveTimeout(properties.readTimeoutMs(), 5_000)));

        return RestClient.builder()
                .baseUrl(resolveBaseUrl(properties.baseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    private long resolveTimeout(int configuredTimeoutMs, int fallbackTimeoutMs) {
        return configuredTimeoutMs > 0 ? configuredTimeoutMs : fallbackTimeoutMs;
    }

    private String resolveBaseUrl(String configuredBaseUrl) {
        return (configuredBaseUrl == null || configuredBaseUrl.isBlank())
                ? "https://provinces.open-api.vn/api/v1"
                : configuredBaseUrl.trim();
    }
}
