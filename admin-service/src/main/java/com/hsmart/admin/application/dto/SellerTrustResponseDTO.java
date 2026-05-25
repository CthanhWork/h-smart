package com.hsmart.admin.application.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerTrustResponseDTO {
    private String sellerId;
    private BigDecimal trustScore;
    private Long reviewCount;
}
