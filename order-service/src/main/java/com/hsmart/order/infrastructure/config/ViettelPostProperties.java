package com.hsmart.order.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "viettel-post")
public record ViettelPostProperties(
        String apiUrl,
        String username,
        String password,
        int connectTimeoutMs,
        int readTimeoutMs,
        int defaultWeightGrams,
        String serviceCode,
        String serviceExtra,
        String productType,
        int orderPayment,
        boolean checkUnique
) {
}
