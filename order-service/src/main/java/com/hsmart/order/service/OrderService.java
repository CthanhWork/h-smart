package com.hsmart.order.service;

import com.hsmart.order.application.dto.CreateOrderRequestDTO;
import com.hsmart.order.application.dto.CreateOfferRequestDTO;
import com.hsmart.order.application.dto.GhtkWebhookRequestDTO;
import com.hsmart.order.application.dto.OfferResponseDTO;
import com.hsmart.order.application.dto.OrderResponseDTO;
import com.hsmart.order.application.dto.OrderStatsResponseDTO;
import com.hsmart.order.application.dto.OrderSummaryDTO;
import com.hsmart.order.application.dto.PageResponseDTO;
import com.hsmart.order.application.dto.ShippingEstimateResponseDTO;
import com.hsmart.order.domain.entities.DeliveryMethod;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface OrderService {
    OrderResponseDTO createOrder(CreateOrderRequestDTO request, String buyerId);
    ShippingEstimateResponseDTO estimateShipping(Long productId, DeliveryMethod deliveryMethod, String buyerId);
    ShippingEstimateResponseDTO estimateGuestShipping(Long productId, DeliveryMethod deliveryMethod, String province, String district);
    OrderResponseDTO completeOrder(Long orderId, String buyerId);
    OrderResponseDTO confirmOrder(Long orderId, String sellerId, List<MultipartFile> evidenceImages);
    OrderResponseDTO cancelOrder(Long orderId, String currentUserId);

    /** Internal: order summary used by payment-service for the seller platform-fee flow. */
    OrderSummaryDTO getOrderSummary(Long orderId);

    /** Internal: flag the order as having its platform fee paid (called after a successful payment). */
    void markSellerShippingPaid(Long orderId);

    // --- Đổi/trả hàng (return) ---
    OrderResponseDTO requestReturn(Long orderId, String buyerId, String reason, List<MultipartFile> returnEvidenceImages);
    OrderResponseDTO sellerApproveReturn(Long orderId, String sellerId);
    OrderResponseDTO sellerRejectReturn(Long orderId, String sellerId, String reason);
    OrderResponseDTO adminApproveReturn(Long orderId);
    OrderResponseDTO adminRejectReturn(Long orderId, String reason);
    void processGhtkWebhook(String hash, GhtkWebhookRequestDTO request);
    PageResponseDTO<OrderResponseDTO> getOrdersForCurrentUser(String currentUserId, Pageable pageable);
    OrderResponseDTO getOrder(Long orderId, String currentUserId);
    OrderResponseDTO getLatestOrderForBuyer(String buyerId);
    OrderStatsResponseDTO getOrderStats();
    OfferResponseDTO createOffer(CreateOfferRequestDTO request, String buyerId);
    PageResponseDTO<OfferResponseDTO> getOffersForCurrentUser(String currentUserId, Pageable pageable);
    OfferResponseDTO acceptOffer(Long offerId, String sellerId);
    OfferResponseDTO rejectOffer(Long offerId, String sellerId);
    OfferResponseDTO cancelOffer(Long offerId, String buyerId);
    PageResponseDTO<OrderResponseDTO> listAllOrdersForAdmin(String status, Pageable pageable);
    OrderResponseDTO adminCancelOrder(Long orderId);
}
