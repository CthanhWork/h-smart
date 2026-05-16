package com.hsmart.backend.infrastructure.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OrderClientConfig {

    @Bean
    @LoadBalanced
    @Qualifier("orderLoadBalancedRestClientBuilder")
    public RestClient.Builder orderLoadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @Qualifier("orderServiceRestClient")
    public RestClient orderServiceRestClient(
            @Qualifier("orderLoadBalancedRestClientBuilder") RestClient.Builder builder,
            OrderServiceProperties properties
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
