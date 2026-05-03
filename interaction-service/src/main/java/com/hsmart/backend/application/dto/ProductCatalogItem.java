package com.hsmart.backend.application.dto;

import java.math.BigDecimal;

public record ProductCatalogItem(
        Long id,
        String title,
        BigDecimal price,
        String description,
        String sellerId,
        String categoryName
) {
}
