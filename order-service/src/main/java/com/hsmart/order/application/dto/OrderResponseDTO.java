package com.hsmart.order.application.dto;

import com.hsmart.order.domain.entities.DeliveryMethod;
import com.hsmart.order.domain.entities.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
    private String productTitle;
    private BigDecimal amount;
    private BigDecimal productAmount;
    private BigDecimal shippingFee;
    private BigDecimal platformFee;
    private boolean sellerShippingFeePaid;
    private String trackingCode;
    private DeliveryMethod deliveryMethod;
    private OrderStatus status;
    private LocalDateTime completedAt;
    private String returnReason;
    private LocalDateTime returnRequestedAt;
    private boolean returnSellerApproved;
    private boolean returnAdminApproved;
    private String returnRejectReason;
    private List<String> evidenceImages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
