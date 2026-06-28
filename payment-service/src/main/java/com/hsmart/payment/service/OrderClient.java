package com.hsmart.payment.service;

import com.hsmart.payment.application.dto.CreateOrderInternalRequestDTO;
import com.hsmart.payment.application.dto.OrderResponseDTO;
import com.hsmart.payment.application.dto.OrderSummaryDTO;
import com.hsmart.payment.application.dto.ShippingEstimateResponseDTO;
import com.hsmart.payment.domain.entities.DeliveryMethod;

public interface OrderClient {

    ShippingEstimateResponseDTO getShippingEstimate(Long productId, DeliveryMethod deliveryMethod, String buyerId);

    OrderResponseDTO createOrderFromDeposit(CreateOrderInternalRequestDTO request, String buyerId);

    /** Fetches the order summary used to drive (and validate) the seller's platform-fee payment. */
    OrderSummaryDTO getOrderSummary(Long orderId);

    /** Best-effort: flag the order as having its platform fee paid so the seller can confirm it. */
    void markSellerShippingPaid(Long orderId);
}
