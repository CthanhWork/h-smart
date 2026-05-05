package com.hsmart.order.service;

import com.hsmart.order.application.dto.CreateOrderRequestDTO;
import com.hsmart.order.application.dto.OrderResponseDTO;

public interface OrderService {
    OrderResponseDTO createOrder(CreateOrderRequestDTO request, String buyerId);
    OrderResponseDTO completeOrder(Long orderId, String buyerId);
    OrderResponseDTO getOrder(Long orderId);
}
