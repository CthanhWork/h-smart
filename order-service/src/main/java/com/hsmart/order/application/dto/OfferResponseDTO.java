package com.hsmart.order.application.dto;

import com.hsmart.order.domain.entities.OfferStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferResponseDTO {
    private Long id;
    private Long productId;
    private String buyerId;
    private String sellerId;
    private BigDecimal originalPrice;
    private BigDecimal offerPrice;
    private Integer discountPercent;
    private OfferStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
