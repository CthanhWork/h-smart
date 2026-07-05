package com.hsmart.payment.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record PaymentOrphanAlertEvent(
        Long depositPaymentId,
        String txnRef,
        String buyerId,
        Long productId,
        BigDecimal amount,
        String errorMessage,
        LocalDateTime paidAt,
        LocalDateTime alertedAt
) {
}
