package com.hsmart.payment.infrastructure.order;

import com.hsmart.payment.application.dto.ApiResponse;
import com.hsmart.payment.application.dto.CreateOrderInternalRequestDTO;
import com.hsmart.payment.application.dto.OrderResponseDTO;
import com.hsmart.payment.application.dto.OrderSummaryDTO;
import com.hsmart.payment.application.dto.ShippingEstimateResponseDTO;
import com.hsmart.payment.application.exceptions.OrderServiceUnavailableException;
import com.hsmart.payment.application.exceptions.PaymentStateException;
import com.hsmart.payment.domain.entities.DeliveryMethod;
import com.hsmart.payment.service.OrderClient;
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
    private static final String USER_ID_HEADER = "X-User-Id";

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
    public ShippingEstimateResponseDTO getShippingEstimate(Long productId, DeliveryMethod deliveryMethod, String buyerId) {
        try {
            ApiResponse<ShippingEstimateResponseDTO> response = orderServiceRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/orders/shipping-estimate")
                            .queryParam("productId", productId)
                            .queryParam("deliveryMethod", deliveryMethod)
                            .build())
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .header(USER_ID_HEADER, buyerId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.getData() == null || response.getData().shippingFee() == null) {
                throw new OrderServiceUnavailableException("Order-service returned an empty shipping estimate");
            }
            return response.getData();
        } catch (HttpClientErrorException exception) {
            // 4xx means the product cannot be ordered (unavailable, own product, etc.) — surface as a payment state error.
            String message = extractMessage(exception, "Product cannot be ordered");
            log.warn("Order-service rejected shipping estimate for product {}: {}", productId, message);
            throw new PaymentStateException(message);
        } catch (RestClientException exception) {
            log.warn("Order-service shipping estimate failed for product {}", productId, exception);
            throw new OrderServiceUnavailableException("Order service is unavailable", exception);
        }
    }

    @Override
    public OrderResponseDTO createOrderFromDeposit(CreateOrderInternalRequestDTO request, String buyerId) {
        try {
            ApiResponse<OrderResponseDTO> response = orderServiceRestClient.post()
                    .uri("/api/v1/orders/internal/from-deposit")
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .header(USER_ID_HEADER, buyerId)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.getData() == null || response.getData().id() == null) {
                throw new OrderServiceUnavailableException("Order-service returned an empty order response");
            }
            return response.getData();
        } catch (HttpClientErrorException exception) {
            String message = extractMessage(exception, "Order could not be created from the deposit");
            log.warn("Order-service rejected order creation for product {}: {}", request.getProductId(), message);
            throw new PaymentStateException(message);
        } catch (RestClientException exception) {
            log.warn("Order-service order creation failed for product {}", request.getProductId(), exception);
            throw new OrderServiceUnavailableException("Order service is unavailable", exception);
        }
    }

    @Override
    public OrderSummaryDTO getOrderSummary(Long orderId) {
        try {
            ApiResponse<OrderSummaryDTO> response = orderServiceRestClient.get()
                    .uri("/api/v1/orders/internal/{orderId}", orderId)
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.getData() == null || response.getData().id() == null) {
                throw new OrderServiceUnavailableException("Order-service returned an empty order summary");
            }
            return response.getData();
        } catch (HttpClientErrorException exception) {
            String message = extractMessage(exception, "Order could not be loaded");
            log.warn("Order-service rejected order summary lookup for order {}: {}", orderId, message);
            throw new PaymentStateException(message);
        } catch (RestClientException exception) {
            log.warn("Order-service order summary lookup failed for order {}", orderId, exception);
            throw new OrderServiceUnavailableException("Order service is unavailable", exception);
        }
    }

    @Override
    public void markSellerShippingPaid(Long orderId) {
        try {
            orderServiceRestClient.post()
                    .uri("/api/v1/orders/internal/{orderId}/platform-fee-paid", orderId)
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Flagged order {} as platform-fee paid", orderId);
        } catch (RestClientException exception) {
            // Best-effort: the payment is already credited; the flag can be reconciled later.
            log.warn("Could not flag order {} as platform-fee paid: {}", orderId, exception.getMessage());
        }
    }

    private String extractMessage(HttpClientErrorException exception, String fallback) {
        if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
            return "Product was not found";
        }
        try {
            ApiResponse<Void> body = exception.getResponseBodyAs(ApiResponse.class);
            if (body != null && body.getMessage() != null) {
                return body.getMessage();
            }
        } catch (RuntimeException ignored) {
            // fall through to default message
        }
        return fallback;
    }
}
