package com.hsmart.order.service;

import com.hsmart.order.application.dto.CreateOrderRequestDTO;
import com.hsmart.order.application.dto.CreateOfferRequestDTO;
import com.hsmart.order.application.dto.GhtkWebhookRequestDTO;
import com.hsmart.order.application.dto.OfferResponseDTO;
import com.hsmart.order.application.dto.OrderResponseDTO;
import com.hsmart.order.application.dto.OrderStatsResponseDTO;
import com.hsmart.order.application.dto.ShippingEstimateResponseDTO;
import com.hsmart.order.domain.entities.DeliveryMethod;
import java.util.List;

public interface OrderService {
    OrderResponseDTO createOrder(CreateOrderRequestDTO request, String buyerId);
    ShippingEstimateResponseDTO estimateShipping(Long productId, DeliveryMethod deliveryMethod, String buyerId);
    ShippingEstimateResponseDTO estimateGuestShipping(Long productId, DeliveryMethod deliveryMethod, String province, String district);
    OrderResponseDTO completeOrder(Long orderId, String buyerId);
    OrderResponseDTO confirmOrder(Long orderId, String sellerId);
    OrderResponseDTO cancelOrder(Long orderId, String currentUserId);
    void processGhtkWebhook(String hash, GhtkWebhookRequestDTO request);
    List<OrderResponseDTO> getOrdersForCurrentUser(String currentUserId);
    OrderResponseDTO getOrder(Long orderId, String currentUserId);
    OrderResponseDTO getLatestOrderForBuyer(String buyerId);
    OrderStatsResponseDTO getOrderStats();
    OfferResponseDTO createOffer(CreateOfferRequestDTO request, String buyerId);
    List<OfferResponseDTO> getOffersForCurrentUser(String currentUserId);
    OfferResponseDTO acceptOffer(Long offerId, String sellerId);
    OfferResponseDTO rejectOffer(Long offerId, String sellerId);
    OfferResponseDTO cancelOffer(Long offerId, String buyerId);
}
