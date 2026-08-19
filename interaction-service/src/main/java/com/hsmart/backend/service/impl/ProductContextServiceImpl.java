package com.hsmart.backend.service.impl;

import com.hsmart.backend.application.dto.AssistantProductContext;
import com.hsmart.backend.application.dto.ProductCatalogItem;
import com.hsmart.backend.application.exceptions.ProductCatalogUnavailableException;
import com.hsmart.backend.service.ProductClient;
import com.hsmart.backend.service.ProductContextService;
import com.hsmart.backend.service.ProductKeywordExtractor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductContextServiceImpl implements ProductContextService {

    public static final String PRODUCT_CATALOG_CIRCUIT_BREAKER = "productCatalogCircuitBreaker";

    public static final String REALTIME_UNAVAILABLE_NOTICE =
            "Hiện tại tôi không thể truy cập dữ liệu thời gian thực, đây là thông tin tham khảo...";

    private static final Locale VIETNAM_LOCALE = Locale.forLanguageTag("vi-VN");

    private final ProductKeywordExtractor productKeywordExtractor;
    private final ProductClient productClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final Tracer tracer;

    @Override
    public AssistantProductContext buildContext(String message, String userId, String traceId) {
        List<String> keywords = productKeywordExtractor.extractKeywords(message);
        if (keywords.isEmpty()) {
            log.info("No product keywords detected for assistant request with traceId {}", traceId);
            return new AssistantProductContext(List.of(), "", false, 0);
        }

        long startedAt = System.nanoTime();
        log.info("Detected product keywords {} for assistant request with traceId {}", keywords, traceId);

        List<ProductCatalogItem> products;
        try {
            products = findProductsWithCircuitBreaker(keywords, userId, traceId);
            log.info("Loaded {} product catalog items for keywords {} with traceId {} in {} ms",
                    products.size(), keywords, traceId, elapsedMillis(startedAt));
        } catch (RuntimeException exception) {
            log.warn("Product catalog circuit breaker fallback triggered for keywords {} with traceId {}. Assistant will answer without realtime product data. Reason: {}",
                    keywords, traceId, exception.getClass().getSimpleName());
            return new AssistantProductContext(
                    keywords,
                    buildUnavailablePrompt(),
                    true,
                    0
            );
        }

        return new AssistantProductContext(
                keywords,
                buildAvailablePrompt(products, keywords),
                false,
                products.size()
        );
    }

    private List<ProductCatalogItem> findProductsWithCircuitBreaker(List<String> keywords, String userId, String traceId) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(PRODUCT_CATALOG_CIRCUIT_BREAKER);
        tagCurrentSpan(circuitBreaker);

        try {
            return circuitBreaker.executeSupplier(() -> productClient.findRelevantProducts(keywords, userId));
        } catch (ProductCatalogUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Product catalog circuit breaker {} rejected or failed a request in state {} with traceId {}. Reason: {}",
                    circuitBreaker.getName(), circuitBreaker.getState(), traceId, exception.getClass().getSimpleName());
            throw new ProductCatalogUnavailableException("Product catalog is unavailable through circuit breaker", exception);
        }
    }

    private void tagCurrentSpan(CircuitBreaker circuitBreaker) {
        Span span = tracer.currentSpan();
        if (span == null) {
            return;
        }

        span.tag("resilience4j.circuit_breaker.name", circuitBreaker.getName());
        span.tag("resilience4j.circuit_breaker.state", circuitBreaker.getState().name());
    }

    private String buildAvailablePrompt(List<ProductCatalogItem> products, List<String> keywords) {
        String productData = products.isEmpty()
                ? "Không tìm thấy sản phẩm đang có phù hợp với từ khóa: " + String.join(", ", keywords) + "."
                : formatProducts(products);

        return "Dưới đây là dữ liệu thực tế từ kho hàng H-Smart: "
                + productData
                + " Hãy sử dụng thông tin này để trả lời người dùng một cách chính xác nhất.";
    }

    private String buildUnavailablePrompt() {
        return REALTIME_UNAVAILABLE_NOTICE
                + " Hãy nói rõ với người dùng bằng tiếng Việt rằng dữ liệu sản phẩm thời gian thực hiện không truy cập được, sau đó đưa ra tư vấn chung.";
    }

    private String formatProducts(List<ProductCatalogItem> products) {
        StringBuilder builder = new StringBuilder("Sản phẩm hiện có:");
        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(VIETNAM_LOCALE);

        for (int index = 0; index < products.size(); index++) {
            ProductCatalogItem product = products.get(index);
            builder.append("\n")
                    .append(index + 1)
                    .append(". ")
                    .append(nullToFallback(product.title(), "Untitled product"))
                    .append(" - Giá: ")
                    .append(product.price() == null ? "Chưa có giá" : currencyFormatter.format(product.price()))
                    .append(" - Danh mục: ")
                    .append(nullToFallback(product.categoryName(), "Chưa phân loại"))
                    .append(" - Mô tả ngắn: ")
                    .append(nullToFallback(product.description(), "Chưa có mô tả"))
                    .append(" - Khu vực: chưa có dữ liệu.");
        }

        return builder.toString();
    }

    private String nullToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
