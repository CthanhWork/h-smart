package com.hsmart.admin.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductCreatedEvent {
    private Long id;
    private String title;
    private String description;
    private BigDecimal price;
    private String categoryName;
    private String status;
    private String sellerId;

    @Builder.Default
    private List<DetectionDTO> aiMetadata = new ArrayList<>();
}
