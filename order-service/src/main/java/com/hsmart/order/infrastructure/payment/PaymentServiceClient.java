package com.hsmart.order.infrastructure.payment;

import com.hsmart.order.service.PaymentClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Notifies payment-service to settle/refund the deposit tied to an order.
 * All calls are best-effort: failures are logged and never roll back the order transaction.
 */
@Slf4j
@Component
public class PaymentServiceClient implements PaymentClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient paymentServiceRestClient;
    private final String internalSharedSecret;

    public PaymentServiceClient(
            @Qualifier("paymentServiceRestClient") RestClient paymentServiceRestClient,
            @Value("${internal.security.secret:}") String internalSharedSecret
    ) {
        this.paymentServiceRestClient = paymentServiceRestClient;
        this.internalSharedSecret = internalSharedSecret;
    }

    @Override
    public void settleDeposit(Long orderId) {
        post(orderId, "settle");
    }

    @Override
    public void refundDeposit(Long orderId) {
        post(orderId, "refund");
    }

    private void post(Long orderId, String action) {
        try {
            paymentServiceRestClient.post()
                    .uri("/api/v1/payments/internal/{orderId}/{action}", orderId, action)
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Requested deposit {} for order {}", action, orderId);
        } catch (RestClientException exception) {
            log.warn("Could not request deposit {} for order {}: {}", action, orderId, exception.getMessage());
        }
    }
}
