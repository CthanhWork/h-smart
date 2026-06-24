package com.hsmart.payment.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vnpay")
public record VnpayProperties(
        String payUrl,
        String tmnCode,
        String hashSecret,
        String returnUrl,
        String resultRedirectUrl,
        String version,
        String command,
        String locale,
        String currencyCode,
        String orderType,
        int expireMinutes
) {
}
