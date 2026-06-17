package com.hsmart.order.application.dto;

import com.hsmart.order.domain.entities.DeliveryMethod;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingEstimateResponseDTO {
    private Long productId;
    private DeliveryMethod deliveryMethod;
    private BigDecimal shippingFee;
    private BigDecimal productPrice;
    private BigDecimal estimatedTotal;
    private String sellerDistrict;
    private String sellerProvince;
}
