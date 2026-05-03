package com.hsmart.gateway.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class GatewayRateLimitConfigTest {

    @Test
    void shouldResolveUserIdFromForwardedHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/assistant/chat")
                        .header("X-User-Id", " user-123 ")
                        .remoteAddress(new InetSocketAddress("192.168.1.10", 12345))
                        .build()
        );

        assertEquals("user-123", GatewayRateLimitConfig.resolveUserOrIpKey(exchange));
    }

    @Test
    void shouldFallbackToClientIpWhenUserHeaderIsMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/assistant/chat")
                        .remoteAddress(new InetSocketAddress("192.168.1.10", 12345))
                        .build()
        );

        assertEquals("192.168.1.10", GatewayRateLimitConfig.resolveUserOrIpKey(exchange));
    }
}
