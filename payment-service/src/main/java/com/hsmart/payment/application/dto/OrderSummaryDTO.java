package com.hsmart.payment.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** Subset of an order-service order used to drive the seller's platform-fee payment. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderSummaryDTO(
        Long id,
        String sellerId,
        Long productId,
        BigDecimal productAmount,
        BigDecimal shippingFee,
        BigDecimal platformFee,
        String status,
        boolean sellerShippingFeePaid
) {
}
