package com.hsmart.payment.application.dto;

import com.hsmart.payment.domain.entities.DeliveryMethod;
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
public class DepositResponseDTO {
    private Long id;
    private String txnRef;
    private Long productId;
    private Long offerId;
    private DeliveryMethod deliveryMethod;
    private BigDecimal amount;
    private PaymentStatus status;
    private Long orderId;
    /** VNPay payment URL the buyer must be redirected to (only present right after creation). */
    private String paymentUrl;
    private LocalDateTime createdAt;
}
