package com.hsmart.order.service;

import com.hsmart.order.application.dto.CreateOrderRequestDTO;
import com.hsmart.order.application.dto.OrderResponseDTO;
import com.hsmart.order.application.dto.OrderStatsResponseDTO;
import com.hsmart.order.application.dto.GhtkWebhookRequestDTO;

public interface OrderService {
    OrderResponseDTO createOrder(CreateOrderRequestDTO request, String buyerId);
    OrderResponseDTO completeOrder(Long orderId, String buyerId);
    OrderResponseDTO confirmOrder(Long orderId, String sellerId);
    void processGhtkWebhook(String hash, GhtkWebhookRequestDTO request);
    OrderResponseDTO getOrder(Long orderId);
    OrderResponseDTO getLatestOrderForBuyer(String buyerId);
    OrderStatsResponseDTO getOrderStats();
}
