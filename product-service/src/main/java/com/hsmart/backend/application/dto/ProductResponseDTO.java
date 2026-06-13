package com.hsmart.backend.application.dto;

import com.hsmart.backend.domain.entities.ProductStatus;
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
public class ProductResponseDTO {
    private Long id;
    private String title;
    private String description;
    private BigDecimal price;
    private ProductStatus status;
    private String sellerId;
    private Long categoryId;
    private String categoryName;
    private String imageUrl;
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();
    private long likeCount;

    @Builder.Default
    private List<DetectionDTO> aiMetadata = new ArrayList<>();

    private Integer numDetections;
}
