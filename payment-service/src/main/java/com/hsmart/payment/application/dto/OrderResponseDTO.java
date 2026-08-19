package com.hsmart.payment.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** Subset of order-service order response; only the created id and amounts are needed. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderResponseDTO(
        Long id,
        BigDecimal amount,
        BigDecimal shippingFee,
        String status
) {
}
