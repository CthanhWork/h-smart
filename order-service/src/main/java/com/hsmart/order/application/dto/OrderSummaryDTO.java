package com.hsmart.order.application.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Minimal order view consumed by payment-service to drive the seller's platform-fee payment. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSummaryDTO {
    private Long id;
    private String sellerId;
    private Long productId;
    private BigDecimal productAmount;
    private BigDecimal shippingFee;
    private BigDecimal platformFee;
    private String status;
    private boolean sellerShippingFeePaid;
}
