package com.hsmart.backend.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "ProductForm", description = "Product form fields sent together with multipart image upload")
public class ProductDTO {
    @Schema(description = "Product name. If omitted on create, backend will suggest one from AI.", example = "Lo vi song")
    private String name;

    @Schema(description = "Product description", example = "Lo vi song cu, con hoat dong tot")
    private String description;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @Schema(description = "Product price", example = "1500000")
    private BigDecimal price;

    @Schema(description = "Optional image URL override. Normally backend sets this from uploaded file.", example = "uploads/example.jpg")
    private String imageUrl;
}
