package com.hsmart.backend.application.dto;

import com.hsmart.backend.domain.entities.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
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
public class ProductRequestDTO {
    private String title;
    private String description;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal price;

    /** When true the listing accepts buyer offers and {@link #minPrice} becomes required. */
    private boolean negotiable;

    /** Lowest price the seller will accept for an offer; ignored when {@link #negotiable} is false. */
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal minPrice;

    private ProductStatus status;
    private Long categoryId;
    private boolean titleModifiedByUser;
}
