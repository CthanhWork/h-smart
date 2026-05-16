package com.hsmart.backend.infrastructure.order;

import com.hsmart.backend.application.dto.ApiResponse;
import com.hsmart.backend.application.dto.OrderSummary;
import com.hsmart.backend.application.exceptions.OrderLookupUnavailableException;
import com.hsmart.backend.service.OrderClient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class OrderServiceClient implements OrderClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient orderServiceRestClient;
    private final String internalSharedSecret;

    public OrderServiceClient(
            @Qualifier("orderServiceRestClient") RestClient orderServiceRestClient,
            @Value("${internal.security.secret:}") String internalSharedSecret
    ) {
        this.orderServiceRestClient = orderServiceRestClient;
        this.internalSharedSecret = internalSharedSecret;
    }

    @Override
    public Optional<OrderSummary> findLatestOrder(String userId) {
        try {
            ApiResponse<OrderResponse> response = orderServiceRestClient.get()
                    .uri("/api/v1/orders/internal/latest")
                    .header("X-User-Id", userId)
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.getData() == null) {
                return Optional.empty();
            }

            return Optional.of(toSummary(response.getData()));
        } catch (HttpClientErrorException.NotFound exception) {
            log.info("No latest order was found for user {}", userId);
            return Optional.empty();
        } catch (RestClientException exception) {
            log.warn("Latest order request failed for user {}", userId, exception);
            throw new OrderLookupUnavailableException("Order lookup is unavailable", exception);
        }
    }

    private OrderSummary toSummary(OrderResponse order) {
        return new OrderSummary(
                order.id(),
                order.buyerId(),
                order.sellerId(),
                order.productId(),
                order.amount(),
                order.status(),
                order.createdAt(),
                order.updatedAt()
        );
    }

    private record OrderResponse(
            Long id,
            String buyerId,
            String sellerId,
            Long productId,
            BigDecimal amount,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
