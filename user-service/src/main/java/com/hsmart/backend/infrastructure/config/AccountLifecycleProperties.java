package com.hsmart.backend.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "account.lifecycle")
public record AccountLifecycleProperties(
        String frontendBaseUrl,
        long verificationTokenMinutes,
        long passwordResetTokenMinutes
) {
}
