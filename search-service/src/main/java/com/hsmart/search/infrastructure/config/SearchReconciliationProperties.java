package com.hsmart.search.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "search.reconciliation")
public record SearchReconciliationProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("0 0 2 * * *") String cron,
        @DefaultValue("100") int batchSize
) {
}
