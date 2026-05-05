package com.hsmart.gateway.infrastructure.filters;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class InternalSecretForwardingFilter implements GlobalFilter, Ordered {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final String internalSharedSecret;

    public InternalSecretForwardingFilter(
            @Value("${internal.security.secret:}") String internalSharedSecret
    ) {
        this.internalSharedSecret = internalSharedSecret;
    }

    @PostConstruct
    void validateConfiguration() {
        if (!StringUtils.hasText(internalSharedSecret)) {
            throw new IllegalStateException("INTERNAL_SHARED_SECRET must be configured for api-gateway");
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        boolean internalRoute = route != null && route.getUri().toString().startsWith("lb:");

        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                .headers(headers -> headers.remove(INTERNAL_SECRET_HEADER));

        if (internalRoute) {
            requestBuilder.headers(headers -> headers.set(INTERNAL_SECRET_HEADER, internalSharedSecret));
        }

        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
