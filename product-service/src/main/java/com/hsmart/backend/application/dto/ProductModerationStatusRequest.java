package com.hsmart.backend.application.dto;

import com.hsmart.backend.domain.entities.ProductStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductModerationStatusRequest {

    @NotNull(message = "Moderation status is required")
    private ProductStatus status;
}
