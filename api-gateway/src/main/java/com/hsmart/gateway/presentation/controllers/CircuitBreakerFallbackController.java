package com.hsmart.gateway.presentation.controllers;

import com.hsmart.gateway.application.dto.ApiResponse;
import com.hsmart.gateway.application.dto.PageResponseDTO;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ServerWebExchange;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/fallback")
public class CircuitBreakerFallbackController {

    public static final String SEARCH_FALLBACK_MESSAGE = "Search service is busy, please try again later.";
    public static final String ASSISTANT_FALLBACK_MESSAGE = "Assistant is currently resting, will be back soon!";
    public static final String PREDICT_FALLBACK_MESSAGE = "Prediction service is busy, please try again later.";

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final Tracer tracer;

    @RequestMapping("/search")
    public ResponseEntity<ApiResponse<PageResponseDTO<Map<String, Object>>>> searchFallback(ServerWebExchange exchange) {
        logFallback("searchCircuitBreaker", "search-service", exchange);
        PageResponseDTO<Map<String, Object>> emptyPage = PageResponseDTO.empty(
                parseInt(exchange.getRequest().getQueryParams().getFirst("page"), DEFAULT_PAGE),
                parseInt(exchange.getRequest().getQueryParams().getFirst("size"), DEFAULT_SIZE)
        );
        return ResponseEntity.ok(ApiResponse.success(200, SEARCH_FALLBACK_MESSAGE, emptyPage));
    }

    @RequestMapping("/assistant")
    public ResponseEntity<ApiResponse<String>> assistantFallback(ServerWebExchange exchange) {
        logFallback("assistantCircuitBreaker", "interaction-service assistant", exchange);
        return ResponseEntity.ok(ApiResponse.success(200, ASSISTANT_FALLBACK_MESSAGE, ASSISTANT_FALLBACK_MESSAGE));
    }

    @RequestMapping("/predict")
    public ResponseEntity<ApiResponse<Void>> predictFallback(ServerWebExchange exchange) {
        logFallback("predictCircuitBreaker", "ai-service prediction", exchange);
        return ResponseEntity.ok(ApiResponse.success(200, PREDICT_FALLBACK_MESSAGE, null));
    }

    private void logFallback(String circuitBreakerName, String downstreamName, ServerWebExchange exchange) {
        Span span = tracer.currentSpan();
        String traceId = span != null ? span.context().traceId() : "unavailable";
        String path = exchange.getRequest().getURI().getRawPath();

        if (span != null) {
            span.tag("resilience4j.circuit_breaker.name", circuitBreakerName);
            span.tag("resilience4j.fallback", "true");
        }

        if (span == null) {
            log.warn("Circuit breaker fallback triggered for {} on path {} with traceId {}",
                    downstreamName, path, traceId);
            return;
        }

        try (
                MDC.MDCCloseable traceIdCloseable = MDC.putCloseable("traceId", span.context().traceId());
                MDC.MDCCloseable spanIdCloseable = MDC.putCloseable("spanId", span.context().spanId())
        ) {
            log.warn("Circuit breaker fallback triggered for {} on path {} with traceId {}",
                    downstreamName, path, traceId);
        }
    }

    private int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
