package com.hsmart.gateway.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;

class CorsConfigTest {

    @Test
    void shouldAllowConfiguredProductionOrigin() {
        CorsConfiguration configuration = new CorsConfig(
                "http://localhost:5173,https://hsmart.thatcherdev.id.vn"
        ).corsConfiguration();

        assertEquals("https://hsmart.thatcherdev.id.vn", configuration.checkOrigin("https://hsmart.thatcherdev.id.vn"));
        assertTrue(configuration.checkHttpMethod(HttpMethod.GET).contains(HttpMethod.GET));
        assertTrue(configuration.checkHttpMethod(HttpMethod.PATCH).contains(HttpMethod.PATCH));
        assertEquals(3600L, configuration.getMaxAge());
    }
}
