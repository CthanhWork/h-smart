package com.hsmart.backend.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDescriptionRequest {

    @NotBlank
    private String productName;

    @NotBlank
    private String category;

    @NotBlank
    private String condition;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal price;
}
