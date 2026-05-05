package com.hsmart.review.infrastructure.order;

import com.hsmart.review.application.dto.ApiResponse;
import com.hsmart.review.application.dto.OrderResponseDTO;
import com.hsmart.review.application.exceptions.OrderVerificationException;
import com.hsmart.review.application.exceptions.OrderVerificationUnavailableException;
import com.hsmart.review.service.OrderClient;
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
    public OrderResponseDTO getOrder(Long orderId, String buyerId) {
        try {
            ApiResponse<OrderResponseDTO> response = orderServiceRestClient.get()
                    .uri("/api/v1/orders/{id}", orderId)
                    .header("X-User-Id", buyerId)
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.getData() == null) {
                throw new OrderVerificationException("Order verification failed");
            }

            return response.getData();
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new OrderVerificationException("Order was not found");
            }
            log.warn("Order-service rejected order verification for order {}", orderId, exception);
            throw new OrderVerificationUnavailableException("Order verification is temporarily unavailable", exception);
        } catch (RestClientException exception) {
            log.warn("Order-service request failed while verifying order {}", orderId, exception);
            throw new OrderVerificationUnavailableException("Order verification is temporarily unavailable", exception);
        }
    }
}
