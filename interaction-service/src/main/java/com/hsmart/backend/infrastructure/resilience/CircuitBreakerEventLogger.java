package com.hsmart.backend.infrastructure.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerEventLogger {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final Tracer tracer;
    private final Set<String> registeredCircuitBreakers = ConcurrentHashMap.newKeySet();

    @PostConstruct
    void registerEventLoggers() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::register);
        circuitBreakerRegistry.getEventPublisher()
                .onEntryAdded(event -> register(event.getAddedEntry()));
    }

    private void register(CircuitBreaker circuitBreaker) {
        if (!registeredCircuitBreakers.add(circuitBreaker.getName())) {
            return;
        }

        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> logCircuitBreakerState(
                        circuitBreaker.getName(),
                        event.getStateTransition().getFromState().name(),
                        event.getStateTransition().getToState().name()
                ))
                .onFailureRateExceeded(event -> log.warn(
                        "Circuit breaker {} failure rate exceeded threshold: {}%",
                        circuitBreaker.getName(),
                        event.getFailureRate()
                ))
                .onSlowCallRateExceeded(event -> log.warn(
                        "Circuit breaker {} slow call rate exceeded threshold: {}%",
                        circuitBreaker.getName(),
                        event.getSlowCallRate()
                ));
    }

    private void logCircuitBreakerState(String circuitBreakerName, String fromState, String toState) {
        Span span = tracer.currentSpan();
        if (span == null) {
            log.warn("Circuit breaker {} state changed from {} to {} with traceId unavailable",
                    circuitBreakerName, fromState, toState);
            return;
        }

        span.tag("resilience4j.circuit_breaker.name", circuitBreakerName);
        span.tag("resilience4j.circuit_breaker.state", toState);

        try (
                MDC.MDCCloseable traceIdCloseable = MDC.putCloseable("traceId", span.context().traceId());
                MDC.MDCCloseable spanIdCloseable = MDC.putCloseable("spanId", span.context().spanId())
        ) {
            log.warn("Circuit breaker {} state changed from {} to {} with traceId {}",
                    circuitBreakerName, fromState, toState, span.context().traceId());
        }
    }
}
