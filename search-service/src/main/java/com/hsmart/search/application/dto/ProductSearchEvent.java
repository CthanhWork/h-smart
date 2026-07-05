package com.hsmart.search.application.dto;

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
public class ProductSearchEvent {
    private Long id;
    private String title;
    private String description;
    private BigDecimal price;
    private String categoryName;
    private String status;
    private String sellerId;
    private String provinceCode;
    private String province;
    private String imageUrl;

    @Builder.Default
    private List<Object> aiMetadata = new ArrayList<>();
}
