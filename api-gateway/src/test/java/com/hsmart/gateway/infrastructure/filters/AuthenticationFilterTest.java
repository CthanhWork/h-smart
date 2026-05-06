package com.hsmart.gateway.infrastructure.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.gateway.infrastructure.config.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class AuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final AuthenticationFilter filterFactory = new AuthenticationFilter(jwtService, new ObjectMapper());

    @Test
    void shouldBypassAuthenticationForWhitelistedPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build()
        );

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = serverWebExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        GatewayFilter filter = filterFactory.apply(new AuthenticationFilter.Config());
        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldBypassAuthenticationForPublicSearchPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/search/products?q=may%20giat").build()
        );

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = serverWebExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        GatewayFilter filter = filterFactory.apply(new AuthenticationFilter.Config());
        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldBypassAuthenticationForPublicReviewReadPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/reviews/sellers/seller-1").build()
        );

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = serverWebExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        GatewayFilter filter = filterFactory.apply(new AuthenticationFilter.Config());
        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldRequireAuthenticationForReviewCreatePath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/reviews").build()
        );

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = serverWebExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        GatewayFilter filter = filterFactory.apply(new AuthenticationFilter.Config());
        filter.filter(exchange, chain).block();

        assertFalse(chainCalled.get());
        assertEquals(401, exchange.getResponse().getStatusCode().value());
    }

    @Test
    void shouldBypassAuthenticationForOptionsRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.OPTIONS, "/api/v1/users/profile").build()
        );

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = serverWebExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        GatewayFilter filter = filterFactory.apply(new AuthenticationFilter.Config());
        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldReturn401WhenAuthorizationHeaderIsMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/profile").build()
        );

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = serverWebExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        GatewayFilter filter = filterFactory.apply(new AuthenticationFilter.Config());
        filter.filter(exchange, chain).block();

        assertFalse(chainCalled.get());
        assertEquals(401, exchange.getResponse().getStatusCode().value());
        assertTrue(exchange.getResponse().getBodyAsString().block().contains("\"message\":\"Invalid or missing security token\""));
    }

    @Test
    void shouldReturn401WhenTokenIsInvalid() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .build()
        );

        when(jwtService.parseClaims("invalid-token")).thenThrow(new JwtException("Invalid token"));

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = serverWebExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        GatewayFilter filter = filterFactory.apply(new AuthenticationFilter.Config());
        filter.filter(exchange, chain).block();

        assertFalse(chainCalled.get());
        assertEquals(401, exchange.getResponse().getStatusCode().value());
        assertTrue(exchange.getResponse().getBodyAsString().block().contains("\"status\":401"));
    }

    @Test
    void shouldForwardXUserIdHeaderWhenTokenIsValid() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .build()
        );

        when(jwtService.parseClaims("valid-token")).thenReturn(claims("nguyenvana", "USER"));

        AtomicReference<ServerWebExchange> forwardedExchange = new AtomicReference<>();
        GatewayFilterChain chain = serverWebExchange -> {
            forwardedExchange.set(serverWebExchange);
            return Mono.empty();
        };

        GatewayFilter filter = filterFactory.apply(new AuthenticationFilter.Config());
        filter.filter(exchange, chain).block();

        assertEquals("nguyenvana", forwardedExchange.get().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("USER", forwardedExchange.get().getRequest().getHeaders().getFirst("X-User-Role"));
    }

    @Test
    void shouldAcceptTokenQueryParameterForWebSocketHandshake() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/interactions/ws?token=ws-valid-token").build()
        );

        when(jwtService.parseClaims("ws-valid-token")).thenReturn(claims("buyer-1", "USER"));

        AtomicReference<ServerWebExchange> forwardedExchange = new AtomicReference<>();
        GatewayFilterChain chain = serverWebExchange -> {
            forwardedExchange.set(serverWebExchange);
            return Mono.empty();
        };

        GatewayFilter filter = filterFactory.apply(new AuthenticationFilter.Config());
        filter.filter(exchange, chain).block();

        assertEquals("buyer-1", forwardedExchange.get().getRequest().getHeaders().getFirst("X-User-Id"));
    }

    @Test
    void shouldRejectAdminRouteWhenRoleIsNotAdmin() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/admin/stats/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                        .build()
        );

        when(jwtService.parseClaims("user-token")).thenReturn(claims("buyer-1", "USER"));

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = serverWebExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        GatewayFilter filter = filterFactory.apply(new AuthenticationFilter.Config());
        filter.filter(exchange, chain).block();

        assertFalse(chainCalled.get());
        assertEquals(403, exchange.getResponse().getStatusCode().value());
        assertTrue(exchange.getResponse().getBodyAsString().block().contains("\"message\":\"Admin role is required\""));
    }

    @Test
    void shouldForwardAdminRouteWhenRoleIsAdmin() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/admin/stats/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                        .build()
        );

        when(jwtService.parseClaims("admin-token")).thenReturn(claims("admin-1", "ADMIN"));

        AtomicReference<ServerWebExchange> forwardedExchange = new AtomicReference<>();
        GatewayFilterChain chain = serverWebExchange -> {
            forwardedExchange.set(serverWebExchange);
            return Mono.empty();
        };

        GatewayFilter filter = filterFactory.apply(new AuthenticationFilter.Config());
        filter.filter(exchange, chain).block();

        assertEquals("admin-1", forwardedExchange.get().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("ADMIN", forwardedExchange.get().getRequest().getHeaders().getFirst("X-User-Role"));
    }

    private Claims claims(String subject, String role) {
        return Jwts.claims()
                .subject(subject)
                .add("role", role)
                .build();
    }
}
