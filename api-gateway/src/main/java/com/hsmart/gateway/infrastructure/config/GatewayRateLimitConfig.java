package com.hsmart.gateway.infrastructure.config;

import java.net.InetSocketAddress;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayRateLimitConfig {

    private static final String USER_HEADER = "X-User-Id";
    private static final String UNKNOWN_CLIENT = "unknown-client";

    @Bean
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> Mono.just(resolveUserOrIpKey(exchange));
    }

    public static String resolveUserOrIpKey(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst(USER_HEADER);
        if (userId != null && !userId.isBlank()) {
            return userId.trim();
        }

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return UNKNOWN_CLIENT;
    }
}
