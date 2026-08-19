package com.hsmart.gateway.filter;

import com.hsmart.gateway.config.RateLimitProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GlobalRateLimiterFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RateLimitProperties rateLimitProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String userId = extractUserId(exchange);
        String ip = extractIp(request);

        // Skip internal endpoints
        if (path.contains("/internal/")) {
            return chain.filter(exchange);
        }

        // Per-user rate limit (if authenticated)
        if (userId != null) {
            return checkRateLimit("user:" + userId, rateLimitProperties.getPerUserRpm(), 60)
                    .flatMap(allowed -> {
                        if (!allowed) {
                            log.warn("User {} exceeded rate limit", userId);
                            return tooManyRequests(exchange, "User rate limit exceeded");
                        }
                        return checkIpAndEndpointLimits(exchange, chain, ip, path);
                    });
        }

        // Per-IP rate limit
        return checkIpAndEndpointLimits(exchange, chain, ip, path);
    }

    private Mono<Void> checkIpAndEndpointLimits(ServerWebExchange exchange, GatewayFilterChain chain, String ip, String path) {
        return checkRateLimit("ip:" + ip, rateLimitProperties.getPerIpRpm(), 60)
                .flatMap(allowed -> {
                    if (!allowed) {
                        log.warn("IP {} exceeded rate limit", ip);
                        return tooManyRequests(exchange, "IP rate limit exceeded");
                    }

                    // Per-endpoint stricter limits
                    if (path.contains("/search")) {
                        return checkEndpointLimit(exchange, chain, ip, "search", path);
                    } else if (path.contains("/products") && exchange.getRequest().getMethod().name().equals("POST")) {
                        return checkEndpointLimit(exchange, chain, ip, "upload", path);
                    } else if (path.contains("/predict")) {
                        return checkEndpointLimit(exchange, chain, ip, "predict", path);
                    }

                    return chain.filter(exchange);
                });
    }

    private Mono<Void> checkEndpointLimit(ServerWebExchange exchange, GatewayFilterChain chain, String ip, String endpoint, String path) {
        Integer limit = rateLimitProperties.getPerEndpoint().get(endpoint);
        if (limit == null) {
            return chain.filter(exchange);
        }

        return checkRateLimit("ip:" + ip + ":" + endpoint, limit, 60)
                .flatMap(allowed -> {
                    if (!allowed) {
                        log.warn("IP {} exceeded {} endpoint rate limit on {}", ip, endpoint, path);
                        return tooManyRequests(exchange, endpoint + " endpoint rate limit exceeded");
                    }
                    return chain.filter(exchange);
                });
    }

    private Mono<Boolean> checkRateLimit(String key, int limit, int windowSeconds) {
        String redisKey = "rate_limit:" + key;

        return redisTemplate.opsForValue()
                .increment(redisKey)
                .flatMap(count -> {
                    if (count == 1) {
                        return redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds))
                                .thenReturn(true);
                    }
                    return Mono.just(count <= limit);
                })
                .onErrorResume(ex -> {
                    log.error("Redis rate limit check failed for key {}: {}", redisKey, ex.getMessage());
                    // Fail open - allow request if Redis is down
                    return Mono.just(true);
                });
    }

    private String extractUserId(ServerWebExchange exchange) {
        return exchange.getAttribute("userId");
    }

    private String extractIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return Objects.requireNonNull(request.getRemoteAddress()).getAddress().getHostAddress();
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        String jsonResponse = String.format(
                "{\"status\":429,\"message\":\"%s\",\"data\":null}",
                message
        );

        DataBuffer buffer = response.bufferFactory()
                .wrap(jsonResponse.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100; // Run early, but after authentication filter
    }
}
