package com.hsmart.order.service;

import com.hsmart.order.application.dto.CreateOrderRequestDTO;
import com.hsmart.order.application.dto.OrderResponseDTO;
import com.hsmart.order.application.dto.OrderStatsResponseDTO;
import com.hsmart.order.application.dto.GhtkWebhookRequestDTO;
import java.util.List;

public interface OrderService {
    OrderResponseDTO createOrder(CreateOrderRequestDTO request, String buyerId);
    OrderResponseDTO completeOrder(Long orderId, String buyerId);
    OrderResponseDTO confirmOrder(Long orderId, String sellerId);
    OrderResponseDTO cancelOrder(Long orderId, String currentUserId);
    void processGhtkWebhook(String hash, GhtkWebhookRequestDTO request);
    List<OrderResponseDTO> getOrdersForCurrentUser(String currentUserId);
    OrderResponseDTO getOrder(Long orderId, String currentUserId);
    OrderResponseDTO getLatestOrderForBuyer(String buyerId);
    OrderStatsResponseDTO getOrderStats();
}
