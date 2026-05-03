package com.hsmart.gateway.infrastructure.filters;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class RequestResponseLoggingFilter implements GlobalFilter, Ordered {

    private final Tracer tracer;

    public RequestResponseLoggingFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getRawPath();
        Span span = tracer.currentSpan();

        logWithSpan(span, "Incoming request: [{}] {}", method, path);

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                    int status = statusCode != null ? statusCode.value() : 0;
                    logWithSpan(span != null ? span : tracer.currentSpan(),
                            "Outgoing response: [{}] {} -> {}", method, path, status);
                });
    }

    private void logWithSpan(Span span, String message, Object... arguments) {
        if (span == null) {
            log.info(message, arguments);
            return;
        }

        try (
                MDC.MDCCloseable traceId = MDC.putCloseable("traceId", span.context().traceId());
                MDC.MDCCloseable spanId = MDC.putCloseable("spanId", span.context().spanId())
        ) {
            log.info(message, arguments);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
