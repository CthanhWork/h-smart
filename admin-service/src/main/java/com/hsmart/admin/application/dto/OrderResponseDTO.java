package com.hsmart.admin.application.dto;

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
public class OrderResponseDTO {
    private Long id;
    private String buyerId;
    private String sellerId;
    private Long productId;
    private BigDecimal amount;
    private BigDecimal productAmount;
    private BigDecimal shippingFee;
    private String trackingCode;
    private String deliveryMethod;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
