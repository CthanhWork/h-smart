package com.hsmart.order.application.dto;

import com.hsmart.order.domain.entities.DeliveryMethod;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequestDTO {

    @NotNull(message = "Product id is required")
    private Long productId;

    @Builder.Default
    private DeliveryMethod deliveryMethod = DeliveryMethod.GHTK;

    private Long offerId;

    public CreateOrderRequestDTO(Long productId) {
        this.productId = productId;
        this.deliveryMethod = DeliveryMethod.GHTK;
    }
}
