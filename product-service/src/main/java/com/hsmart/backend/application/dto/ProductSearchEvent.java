package com.hsmart.backend.application.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchEvent {
    private Long id;
    private String title;
    private String description;
    private BigDecimal price;
    private String categoryName;
    private String status;
    private String sellerId;
    private String imageUrl;

    @Builder.Default
    private List<DetectionDTO> aiMetadata = new ArrayList<>();
}
