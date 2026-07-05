package com.hsmart.search.infrastructure.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.search.application.dto.ProductSearchEvent;
import com.hsmart.search.infrastructure.config.ProductServiceProperties;
import com.hsmart.search.service.ProductClient;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductServiceClient implements ProductClient {

    private final ProductServiceProperties productServiceProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public ProductServiceClient(
            ProductServiceProperties productServiceProperties,
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder
    ) {
        this.productServiceProperties = productServiceProperties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .rootUri(productServiceProperties.baseUrl())
                .setConnectTimeout(Duration.ofMillis(productServiceProperties.connectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(productServiceProperties.readTimeoutMs()))
                .build();
    }

    @Override
    public List<ProductSearchEvent> getAllVisibleProducts() {
        try {
            String url = "/api/v1/products/internal/search-reconciliation";
            String response = restTemplate.getForObject(url, String.class);

            if (response == null) {
                log.warn("Product service returned null response for reconciliation");
                return Collections.emptyList();
            }

            // Parse the API response wrapper
            var wrapper = objectMapper.readValue(response, new TypeReference<ApiResponse<List<ProductSearchEvent>>>() {});
            return wrapper.data() != null ? wrapper.data() : Collections.emptyList();

        } catch (IOException ex) {
            log.error("Failed to parse product reconciliation response: {}", ex.getMessage(), ex);
            return Collections.emptyList();
        } catch (Exception ex) {
            log.error("Failed to fetch products for reconciliation: {}", ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    private record ApiResponse<T>(int status, String message, T data) {
    }
}
