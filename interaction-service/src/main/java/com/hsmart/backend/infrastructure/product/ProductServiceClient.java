package com.hsmart.backend.infrastructure.product;

import com.hsmart.backend.application.dto.ApiResponse;
import com.hsmart.backend.application.dto.ProductCatalogItem;
import com.hsmart.backend.application.exceptions.ProductCatalogUnavailableException;
import com.hsmart.backend.infrastructure.config.ProductServiceProperties;
import com.hsmart.backend.service.ProductClient;
import com.hsmart.backend.service.ProductKeywordExtractor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class ProductServiceClient implements ProductClient {

    private static final int MAX_PROMPT_PRODUCTS = 5;
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient productServiceRestClient;
    private final ProductServiceProperties productServiceProperties;
    private final ProductKeywordExtractor productKeywordExtractor;
    private final String internalSharedSecret;

    public ProductServiceClient(
            @Qualifier("productServiceRestClient") RestClient productServiceRestClient,
            ProductServiceProperties productServiceProperties,
            ProductKeywordExtractor productKeywordExtractor,
            @Value("${internal.security.secret:}") String internalSharedSecret
    ) {
        this.productServiceRestClient = productServiceRestClient;
        this.productServiceProperties = productServiceProperties;
        this.productKeywordExtractor = productKeywordExtractor;
        this.internalSharedSecret = internalSharedSecret;
    }

    @Override
    public List<ProductCatalogItem> findRelevantProducts(List<String> keywords, String userId) {
        Map<Long, ProductCatalogItem> productsById = new LinkedHashMap<>();

        for (String keyword : keywords) {
            fetchProducts(keyword, userId).stream()
                    .filter(item -> productKeywordExtractor.matchesAnyKeyword(item, List.of(keyword)))
                    .forEach(item -> productsById.putIfAbsent(item.id(), item));

            if (productsById.size() >= MAX_PROMPT_PRODUCTS) {
                break;
            }
        }

        return new ArrayList<>(productsById.values()).stream()
                .limit(MAX_PROMPT_PRODUCTS)
                .toList();
    }

    private List<ProductCatalogItem> fetchProducts(String keyword, String userId) {
        try {
            ApiResponse<ProductPageResponse<ProductResponse>> response = productServiceRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/products")
                            .queryParam("page", 0)
                            .queryParam("size", productServiceProperties.pageSize())
                            .queryParam("sort", "id,desc")
                            .queryParam("keyword", keyword)
                            .queryParam("status", ACTIVE_STATUS)
                            .build())
                    .header("X-User-Id", userId)
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.getData() == null || response.getData().content() == null) {
                return List.of();
            }

            return response.getData().content().stream()
                    .filter(Objects::nonNull)
                    .filter(product -> product.status() == null || ACTIVE_STATUS.equalsIgnoreCase(product.status()))
                    .map(this::toCatalogItem)
                    .toList();
        } catch (RestClientException exception) {
            log.warn("Product catalog request failed while searching keyword {}", keyword, exception);
            throw new ProductCatalogUnavailableException("Product catalog is unavailable", exception);
        }
    }

    private ProductCatalogItem toCatalogItem(ProductResponse product) {
        return new ProductCatalogItem(
                product.id(),
                product.title(),
                product.price(),
                shortDescription(product.description()),
                product.sellerId(),
                product.categoryName()
        );
    }

    private String shortDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return "No short description is available";
        }

        String normalized = description.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 160) {
            return normalized;
        }
        return normalized.substring(0, 157) + "...";
    }

    private record ProductPageResponse<T>(
            List<T> content,
            int pageNo,
            int pageSize,
            long totalElements,
            int totalPages,
            boolean last
    ) {
    }

    private record ProductResponse(
            Long id,
            String title,
            String description,
            BigDecimal price,
            String status,
            String sellerId,
            Long categoryId,
            String categoryName
    ) {
    }
}
