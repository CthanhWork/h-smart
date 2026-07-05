package com.hsmart.gateway.config;

import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private int perUserRpm = 100;
    private int perIpRpm = 200;
    private Map<String, Integer> perEndpoint = Map.of(
            "search", 20,
            "upload", 10,
            "predict", 15
    );
}
