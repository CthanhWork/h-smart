package com.hsmart.payment.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePlatformFeeRequestDTO {

    @NotNull(message = "Order id is required")
    private Long orderId;
}
