package com.hsmart.payment.application.dto;

import com.hsmart.payment.domain.entities.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformFeeResponseDTO {
    private Long id;
    private String txnRef;
    private Long orderId;
    private BigDecimal amount;
    private PaymentStatus status;
    /** VNPay payment URL the seller must be redirected to (only present right after creation). */
    private String paymentUrl;
    private LocalDateTime createdAt;
}
