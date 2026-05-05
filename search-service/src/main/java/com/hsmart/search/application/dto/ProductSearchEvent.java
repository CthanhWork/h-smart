package com.hsmart.search.application.dto;

import java.math.BigDecimal;
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
}
