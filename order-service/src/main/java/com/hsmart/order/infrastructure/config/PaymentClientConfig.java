package com.hsmart.order.infrastructure.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class PaymentClientConfig {

    @Bean
    @LoadBalanced
    @Qualifier("paymentLoadBalancedRestClientBuilder")
    public RestClient.Builder paymentLoadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @Qualifier("paymentServiceRestClient")
    public RestClient paymentServiceRestClient(
            @Qualifier("paymentLoadBalancedRestClientBuilder") RestClient.Builder builder,
            PaymentServiceProperties properties
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
