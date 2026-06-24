package com.hsmart.payment.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** Subset of order-service shipping estimate; only the deposit amount is needed. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShippingEstimateResponseDTO(
        Long productId,
        BigDecimal shippingFee,
        BigDecimal productPrice,
        BigDecimal estimatedTotal
) {
}
