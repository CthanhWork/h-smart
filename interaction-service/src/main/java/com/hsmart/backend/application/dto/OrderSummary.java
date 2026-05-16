package com.hsmart.backend.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderSummary(
        Long id,
        String buyerId,
        String sellerId,
        Long productId,
        BigDecimal amount,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
