package com.hsmart.admin.infrastructure.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class DownstreamClientConfig {

    @Bean
    @LoadBalanced
    @Qualifier("loadBalancedRestClientBuilder")
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public ServiceClientProperties productServiceProperties(
            @Value("${product-service.base-url:http://product-service}") String baseUrl,
            @Value("${product-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${product-service.read-timeout-ms:3000}") int readTimeoutMs
    ) {
        return new ServiceClientProperties(baseUrl, connectTimeoutMs, readTimeoutMs);
    }

    @Bean
    public ServiceClientProperties userServiceProperties(
            @Value("${user-service.base-url:http://user-service}") String baseUrl,
            @Value("${user-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${user-service.read-timeout-ms:3000}") int readTimeoutMs
    ) {
        return new ServiceClientProperties(baseUrl, connectTimeoutMs, readTimeoutMs);
    }

    @Bean
    public ServiceClientProperties orderServiceProperties(
            @Value("${order-service.base-url:http://order-service}") String baseUrl,
            @Value("${order-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${order-service.read-timeout-ms:3000}") int readTimeoutMs
    ) {
        return new ServiceClientProperties(baseUrl, connectTimeoutMs, readTimeoutMs);
    }

    @Bean
    public ServiceClientProperties paymentServiceProperties(
            @Value("${payment-service.base-url:http://payment-service}") String baseUrl,
            @Value("${payment-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${payment-service.read-timeout-ms:3000}") int readTimeoutMs
    ) {
        return new ServiceClientProperties(baseUrl, connectTimeoutMs, readTimeoutMs);
    }

    @Bean
    public ServiceClientProperties reviewServiceProperties(
            @Value("${review-service.base-url:http://review-service}") String baseUrl,
            @Value("${review-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${review-service.read-timeout-ms:3000}") int readTimeoutMs
    ) {
        return new ServiceClientProperties(baseUrl, connectTimeoutMs, readTimeoutMs);
    }

    @Bean
    @Qualifier("productServiceRestClient")
    public RestClient productServiceRestClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder,
            @Qualifier("productServiceProperties") ServiceClientProperties properties
    ) {
        return buildClient(builder, properties);
    }

    @Bean
    @Qualifier("userServiceRestClient")
    public RestClient userServiceRestClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder,
            @Qualifier("userServiceProperties") ServiceClientProperties properties
    ) {
        return buildClient(builder, properties);
    }

    @Bean
    @Qualifier("orderServiceRestClient")
    public RestClient orderServiceRestClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder,
            @Qualifier("orderServiceProperties") ServiceClientProperties properties
    ) {
        return buildClient(builder, properties);
    }

    @Bean
    @Qualifier("paymentServiceRestClient")
    public RestClient paymentServiceRestClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder,
            @Qualifier("paymentServiceProperties") ServiceClientProperties properties
    ) {
        return buildClient(builder, properties);
    }

    @Bean
    @Qualifier("reviewServiceRestClient")
    public RestClient reviewServiceRestClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder,
            @Qualifier("reviewServiceProperties") ServiceClientProperties properties
    ) {
        return buildClient(builder, properties);
    }

    private RestClient buildClient(RestClient.Builder builder, ServiceClientProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
