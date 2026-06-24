package com.hsmart.admin.infrastructure.order;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.admin.application.dto.ApiResponse;
import com.hsmart.admin.application.dto.OrderResponseDTO;
import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.application.exceptions.MarketplaceStatsUnavailableException;
import com.hsmart.admin.service.OrderAdminClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class OrderServiceAdminClient implements OrderAdminClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient orderServiceRestClient;
    private final String internalSharedSecret;
    private final ObjectMapper objectMapper;

    public OrderServiceAdminClient(
            @Qualifier("orderServiceRestClient") RestClient orderServiceRestClient,
            @Value("${internal.security.secret:}") String internalSharedSecret,
            ObjectMapper objectMapper
    ) {
        this.orderServiceRestClient = orderServiceRestClient;
        this.internalSharedSecret = internalSharedSecret;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResponseDTO<OrderResponseDTO> listAllOrders(String status, Pageable pageable) {
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/api/v1/orders/internal/admin/list")
                    .queryParam("page", pageable.getPageNumber())
                    .queryParam("size", pageable.getPageSize());
            if (status != null && !status.isBlank()) {
                uriBuilder.queryParam("status", status);
            }

            ApiResponse<Object> response = orderServiceRestClient.get()
                    .uri(uriBuilder.toUriString())
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.getData() == null) {
                return PageResponseDTO.<OrderResponseDTO>builder().content(java.util.List.of()).build();
            }

            PageResponseDTO<OrderResponseDTO> page = objectMapper.convertValue(
                    response.getData(),
                    new TypeReference<PageResponseDTO<OrderResponseDTO>>() {}
            );
            log.info("Order-service list all orders completed, page {}", pageable.getPageNumber());
            return page;
        } catch (RestClientException | IllegalArgumentException exception) {
            log.error("Order-service list all orders failed", exception);
            throw new MarketplaceStatsUnavailableException("Order list unavailable", exception);
        }
    }

    @Override
    public OrderResponseDTO adminCancelOrder(Long orderId) {
        try {
            ApiResponse<Object> response = orderServiceRestClient.post()
                    .uri("/api/v1/orders/internal/admin/{orderId}/cancel", orderId)
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.getData() == null) {
                throw new MarketplaceStatsUnavailableException("Order-service returned empty cancel response", null);
            }
            OrderResponseDTO order = objectMapper.convertValue(response.getData(), OrderResponseDTO.class);
            log.info("Order-service admin cancel completed for order {}", orderId);
            return order;
        } catch (RestClientException | IllegalArgumentException exception) {
            log.error("Order-service admin cancel failed for order {}", orderId, exception);
            throw new MarketplaceStatsUnavailableException("Order cancel failed", exception);
        }
    }

    @Override
    public OrderResponseDTO adminApproveReturn(Long orderId) {
        try {
            ApiResponse<Object> response = orderServiceRestClient.post()
                    .uri("/api/v1/orders/internal/admin/{orderId}/return-approve", orderId)
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.getData() == null) {
                throw new MarketplaceStatsUnavailableException("Order-service returned empty return-approve response", null);
            }
            OrderResponseDTO order = objectMapper.convertValue(response.getData(), OrderResponseDTO.class);
            log.info("Order-service admin return-approve completed for order {}", orderId);
            return order;
        } catch (RestClientException | IllegalArgumentException exception) {
            log.error("Order-service admin return-approve failed for order {}", orderId, exception);
            throw new MarketplaceStatsUnavailableException("Order return approval failed", exception);
        }
    }

    @Override
    public OrderResponseDTO adminRejectReturn(Long orderId, String reason) {
        try {
            ApiResponse<Object> response = orderServiceRestClient.post()
                    .uri("/api/v1/orders/internal/admin/{orderId}/return-reject", orderId)
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .body(java.util.Map.of("reason", reason == null ? "" : reason))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.getData() == null) {
                throw new MarketplaceStatsUnavailableException("Order-service returned empty return-reject response", null);
            }
            OrderResponseDTO order = objectMapper.convertValue(response.getData(), OrderResponseDTO.class);
            log.info("Order-service admin return-reject completed for order {}", orderId);
            return order;
        } catch (RestClientException | IllegalArgumentException exception) {
            log.error("Order-service admin return-reject failed for order {}", orderId, exception);
            throw new MarketplaceStatsUnavailableException("Order return rejection failed", exception);
        }
    }
}
