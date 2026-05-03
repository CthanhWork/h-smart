package com.hsmart.gateway.infrastructure.filters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.gateway.application.dto.ApiResponse;
import com.hsmart.gateway.infrastructure.config.GatewayRateLimitConfig;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitResponseFilter implements GlobalFilter, Ordered {

    public static final String RATE_LIMIT_MESSAGE =
            "Too many AI requests. Please wait a moment before trying again.";

    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponseDecorator responseDecorator =
                new ServerHttpResponseDecorator(exchange.getResponse()) {
                    @Override
                    public Mono<Void> setComplete() {
                        if (HttpStatus.TOO_MANY_REQUESTS.equals(getStatusCode()) && !isCommitted()) {
                            logRateLimitedRequest(exchange);
                            getHeaders().setContentType(MediaType.APPLICATION_JSON);

                            byte[] body = toJsonBytes(ApiResponse.error(
                                    HttpStatus.TOO_MANY_REQUESTS.value(),
                                    RATE_LIMIT_MESSAGE
                            ));
                            getHeaders().setContentLength(body.length);
                            DataBuffer buffer = bufferFactory().wrap(body);
                            return writeWith(Mono.just(buffer));
                        }

                        return super.setComplete();
                    }
                };

        return chain.filter(exchange.mutate().response(responseDecorator).build());
    }

    private void logRateLimitedRequest(ServerWebExchange exchange) {
        Span span = tracer.currentSpan();
        String traceId = span != null ? span.context().traceId() : "unavailable";
        String key = GatewayRateLimitConfig.resolveUserOrIpKey(exchange);
        String path = exchange.getRequest().getURI().getRawPath();

        if (span == null) {
            log.warn("Rate limited AI request for key {} on path {} with traceId {}", key, path, traceId);
            return;
        }

        try (
                MDC.MDCCloseable traceIdCloseable = MDC.putCloseable("traceId", span.context().traceId());
                MDC.MDCCloseable spanIdCloseable = MDC.putCloseable("spanId", span.context().spanId())
        ) {
            log.warn("Rate limited AI request for key {} on path {} with traceId {}", key, path, traceId);
        }
    }

    private byte[] toJsonBytes(ApiResponse<Void> response) {
        try {
            return objectMapper.writeValueAsBytes(response);
        } catch (JsonProcessingException exception) {
            return "{\"status\":429,\"message\":\"Too many AI requests. Please wait a moment before trying again.\",\"data\":null}"
                    .getBytes();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
