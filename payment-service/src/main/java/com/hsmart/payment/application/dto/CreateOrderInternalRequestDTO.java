package com.hsmart.payment.application.dto;

import com.hsmart.payment.domain.entities.DeliveryMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Payload sent to order-service to create the order once the deposit is paid. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderInternalRequestDTO {
    private Long productId;
    private DeliveryMethod deliveryMethod;
    private Long offerId;
    private Long depositPaymentId;
}
