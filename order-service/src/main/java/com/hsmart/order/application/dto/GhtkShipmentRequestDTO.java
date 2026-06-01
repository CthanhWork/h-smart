package com.hsmart.order.application.dto;

import java.math.BigDecimal;

public record GhtkShipmentRequestDTO(
        String partnerOrderId,
        String productName,
        BigDecimal productValue,
        BigDecimal codAmount,
        UserAddressResponseDTO sellerAddress,
        UserAddressResponseDTO buyerAddress
) {
}
