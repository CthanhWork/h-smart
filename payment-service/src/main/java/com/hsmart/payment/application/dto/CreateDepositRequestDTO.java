package com.hsmart.payment.application.dto;

import com.hsmart.payment.domain.entities.DeliveryMethod;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDepositRequestDTO {

    @NotNull(message = "Product id is required")
    private Long productId;

    @Builder.Default
    private DeliveryMethod deliveryMethod = DeliveryMethod.VIETTEL_POST;

    /** Optional accepted offer to settle the product price at checkout. */
    private Long offerId;
}
