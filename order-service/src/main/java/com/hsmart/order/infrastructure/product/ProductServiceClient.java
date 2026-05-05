package com.hsmart.order.infrastructure.product;

import com.hsmart.order.application.dto.ApiResponse;
import com.hsmart.order.application.dto.ProductResponseDTO;
import com.hsmart.order.application.exceptions.ProductCatalogUnavailableException;
import com.hsmart.order.application.exceptions.ProductUnavailableException;
import com.hsmart.order.service.ProductClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class ProductServiceClient implements ProductClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient productServiceRestClient;
    private final String internalSharedSecret;

    public ProductServiceClient(
            @Qualifier("productServiceRestClient") RestClient productServiceRestClient,
            @Value("${internal.security.secret:}") String internalSharedSecret
    ) {
        this.productServiceRestClient = productServiceRestClient;
        this.internalSharedSecret = internalSharedSecret;
    }

    @Override
    public ProductResponseDTO getProduct(Long productId, String buyerId) {
        try {
            ApiResponse<ProductResponseDTO> response = productServiceRestClient.get()
                    .uri("/api/v1/products/{id}", productId)
                    .header("X-User-Id", buyerId)
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.getData() == null) {
                throw new ProductUnavailableException("Product information is unavailable");
            }

            return response.getData();
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ProductUnavailableException("Product was not found");
            }
            log.warn("Product-service rejected product lookup for product {}", productId, exception);
            throw new ProductCatalogUnavailableException("Product catalog is unavailable", exception);
        } catch (RestClientException exception) {
            log.warn("Product-service request failed while creating an order for product {}", productId, exception);
            throw new ProductCatalogUnavailableException("Product catalog is unavailable", exception);
        }
    }
}
