package com.hsmart.order.service;

import com.hsmart.order.application.dto.GhtkShipmentRequestDTO;
import com.hsmart.order.application.dto.UserAddressResponseDTO;
import com.hsmart.order.domain.entities.DeliveryMethod;
import java.math.BigDecimal;

public interface GhtkClient extends ShippingProviderClient {
    @Override
    default DeliveryMethod deliveryMethod() {
        return DeliveryMethod.GHTK;
    }

    @Override
    BigDecimal calculateShippingFee(UserAddressResponseDTO sellerAddress, UserAddressResponseDTO buyerAddress);

    @Override
    String createShipment(GhtkShipmentRequestDTO request);
}
