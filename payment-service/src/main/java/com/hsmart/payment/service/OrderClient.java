package com.hsmart.payment.service;

import com.hsmart.payment.application.dto.CreateOrderInternalRequestDTO;
import com.hsmart.payment.application.dto.OrderResponseDTO;
import com.hsmart.payment.application.dto.ShippingEstimateResponseDTO;
import com.hsmart.payment.domain.entities.DeliveryMethod;

public interface OrderClient {

    ShippingEstimateResponseDTO getShippingEstimate(Long productId, DeliveryMethod deliveryMethod, String buyerId);

    OrderResponseDTO createOrderFromDeposit(CreateOrderInternalRequestDTO request, String buyerId);
}
