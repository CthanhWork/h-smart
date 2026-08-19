package com.hsmart.order.application.dto;

import java.math.BigDecimal;

public record ProductResponseDTO(
        Long id,
        String title,
        String description,
        BigDecimal price,
        String status,
        String sellerId,
        Long categoryId,
        String categoryName,
        String imageUrl,
        boolean negotiable,
        BigDecimal minPrice
) {
}
