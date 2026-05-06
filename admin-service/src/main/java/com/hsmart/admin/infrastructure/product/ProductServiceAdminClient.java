package com.hsmart.admin.infrastructure.product;

import com.hsmart.admin.application.dto.ApiResponse;
import com.hsmart.admin.application.dto.ProductModerationStatusRequest;
import com.hsmart.admin.application.exceptions.ProductModerationException;
import com.hsmart.admin.service.ProductAdminClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class ProductServiceAdminClient implements ProductAdminClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient productServiceRestClient;
    private final String internalSharedSecret;

    public ProductServiceAdminClient(
            @Qualifier("productServiceRestClient") RestClient productServiceRestClient,
            @Value("${internal.security.secret:}") String internalSharedSecret
    ) {
        this.productServiceRestClient = productServiceRestClient;
        this.internalSharedSecret = internalSharedSecret;
    }

    @Override
    public void updateModerationStatus(Long productId, String status) {
        try {
            productServiceRestClient.put()
                    .uri("/api/v1/products/internal/{id}/moderation-status", productId)
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .body(new ProductModerationStatusRequest(status))
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<Object>>() {
                    });
            log.info("Product-service moderation status update completed for product {}", productId);
        } catch (RestClientException exception) {
            log.error("Product-service moderation status update failed for product {}", productId, exception);
            throw new ProductModerationException("Product moderation status update failed", exception);
        }
    }
}
