package com.hsmart.order.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOfferRequestDTO {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Discount percent is required")
    @Min(value = 1, message = "Discount percent must be at least 1")
    @Max(value = 99, message = "Discount percent must be at most 99")
    private Integer discountPercent;
}
