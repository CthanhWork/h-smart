package com.hsmart.gateway.infrastructure.filters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.gateway.application.dto.ApiResponse;
import com.hsmart.gateway.infrastructure.config.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INVALID_TOKEN_MESSAGE = "Invalid or missing security token";
    private static final String ADMIN_REQUIRED_MESSAGE = "Admin role is required";
    private static final String WEBSOCKET_PATH_PREFIX = "/api/v1/interactions/ws";
    private static final String ADMIN_PATH_PREFIX = "/api/v1/admin/";
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/**",
            "/api/v1/products/media/**",
            "/api/v1/search/**",
            "/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    );
    private static final List<String> PUBLIC_GET_PATHS = List.of(
            "/api/v1/reviews/**"
    );

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        super(Config.class);
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getPath();

            if (isPublicRequest(exchange, path)) {
                return chain.filter(exchange);
            }

            String authorization = resolveAuthorizationHeader(exchange, path);
            if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
                log.warn("Authentication rejected for path {} because the Authorization header is missing or malformed", path);
                return writeUnauthorizedResponse(exchange);
            }

            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            if (token.isEmpty()) {
                log.warn("Authentication rejected for path {} because the bearer token is empty", path);
                return writeUnauthorizedResponse(exchange);
            }

            try {
                Claims claims = jwtService.parseClaims(token);
                String username = claims.getSubject();
                if (username == null || username.isBlank()) {
                    log.warn("Authentication rejected for path {} because the token subject is missing", path);
                    return writeUnauthorizedResponse(exchange);
                }
                String role = resolveRole(claims);
                if (path.startsWith(ADMIN_PATH_PREFIX) && !"ADMIN".equalsIgnoreCase(role)) {
                    log.warn("Authorization rejected for path {} because the caller role is not ADMIN", path);
                    return writeForbiddenResponse(exchange);
                }

                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                        .header("X-User-Id", username)
                        .header("X-User-Role", role)
                        .build();

                log.info("Authentication successful for path {}. Forwarding X-User-Id header", path);
                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            } catch (JwtException | IllegalArgumentException exception) {
                log.warn("Authentication rejected for path {} because the JWT is invalid or expired", path);
                return writeUnauthorizedResponse(exchange);
            }
        };
    }

    private boolean isPublicRequest(ServerWebExchange exchange, String path) {
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return true;
        }

        return PUBLIC_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path))
                || (HttpMethod.GET.equals(exchange.getRequest().getMethod())
                && PUBLIC_GET_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path)));
    }

    private String resolveAuthorizationHeader(ServerWebExchange exchange, String path) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null) {
            return authorization;
        }

        if (path.startsWith(WEBSOCKET_PATH_PREFIX)) {
            String token = exchange.getRequest().getQueryParams().getFirst("token");
            if (token != null && !token.isBlank()) {
                return BEARER_PREFIX + token.trim();
            }
        }

        return null;
    }

    private Mono<Void> writeUnauthorizedResponse(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] payload = toJsonBytes(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), INVALID_TOKEN_MESSAGE));
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(payload);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Mono<Void> writeForbiddenResponse(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] payload = toJsonBytes(ApiResponse.error(HttpStatus.FORBIDDEN.value(), ADMIN_REQUIRED_MESSAGE));
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(payload);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String resolveRole(Claims claims) {
        Object role = claims.get("role");
        return role == null ? "" : role.toString();
    }

    private byte[] toJsonBytes(ApiResponse<Void> body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException exception) {
            return "{\"status\":401,\"message\":\"Invalid or missing security token\",\"data\":null}"
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    public static class Config {
    }
}
