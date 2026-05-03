package com.hsmart.gateway.infrastructure.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class RateLimitResponseFilterTest {

    private final Tracer tracer = mock(Tracer.class);
    private final RateLimitResponseFilter filter = new RateLimitResponseFilter(new ObjectMapper(), tracer);

    @Test
    void shouldWriteStandardApiResponseForTooManyRequests() {
        when(tracer.currentSpan()).thenReturn(null);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/assistant/chat")
                        .header("X-User-Id", "user-123")
                        .build()
        );

        GatewayFilterChain chain = serverWebExchange -> {
            serverWebExchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return serverWebExchange.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        String body = exchange.getResponse().getBodyAsString().block();
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertTrue(body.contains("\"status\":429"));
        assertTrue(body.contains("\"message\":\"Too many AI requests. Please wait a moment before trying again.\""));
        assertTrue(body.contains("\"data\":null"));
    }
}
