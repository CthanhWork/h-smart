package com.hsmart.admin.infrastructure.stats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.admin.application.dto.ApiResponse;
import com.hsmart.admin.application.dto.OrderStatsResponseDTO;
import com.hsmart.admin.application.dto.ProductStatsResponseDTO;
import com.hsmart.admin.application.dto.UserStatsResponseDTO;
import com.hsmart.admin.application.exceptions.MarketplaceStatsUnavailableException;
import com.hsmart.admin.service.StatsClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class DownstreamStatsClient implements StatsClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient userServiceRestClient;
    private final RestClient productServiceRestClient;
    private final RestClient orderServiceRestClient;
    private final String internalSharedSecret;
    private final ObjectMapper objectMapper;

    public DownstreamStatsClient(
            @Qualifier("userServiceRestClient") RestClient userServiceRestClient,
            @Qualifier("productServiceRestClient") RestClient productServiceRestClient,
            @Qualifier("orderServiceRestClient") RestClient orderServiceRestClient,
            @Value("${internal.security.secret:}") String internalSharedSecret,
            ObjectMapper objectMapper
    ) {
        this.userServiceRestClient = userServiceRestClient;
        this.productServiceRestClient = productServiceRestClient;
        this.orderServiceRestClient = orderServiceRestClient;
        this.internalSharedSecret = internalSharedSecret;
        this.objectMapper = objectMapper;
    }

    @Override
    public UserStatsResponseDTO getUserStats() {
        return getStats(userServiceRestClient, "/api/v1/users/internal/stats", UserStatsResponseDTO.class, "user-service");
    }

    @Override
    public ProductStatsResponseDTO getProductStats() {
        return getStats(productServiceRestClient, "/api/v1/products/internal/stats", ProductStatsResponseDTO.class, "product-service");
    }

    @Override
    public OrderStatsResponseDTO getOrderStats() {
        return getStats(orderServiceRestClient, "/api/v1/orders/internal/stats", OrderStatsResponseDTO.class, "order-service");
    }

    private <T> T getStats(RestClient restClient, String uri, Class<T> dataType, String serviceName) {
        try {
            ApiResponse<T> response = restClient.get()
                    .uri(uri)
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.getData() == null) {
                throw new MarketplaceStatsUnavailableException(serviceName + " returned an empty stats response", null);
            }

            return objectMapper.convertValue(response.getData(), dataType);
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("{} stats request failed", serviceName, exception);
            throw new MarketplaceStatsUnavailableException(serviceName + " stats are unavailable", exception);
        }
    }
}
