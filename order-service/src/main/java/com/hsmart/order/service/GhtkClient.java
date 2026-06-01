package com.hsmart.order.service;

import com.hsmart.order.application.dto.GhtkShipmentRequestDTO;
import com.hsmart.order.application.dto.UserAddressResponseDTO;
import java.math.BigDecimal;

public interface GhtkClient {
    BigDecimal calculateShippingFee(UserAddressResponseDTO sellerAddress, UserAddressResponseDTO buyerAddress);
    String createShipment(GhtkShipmentRequestDTO request);
}
