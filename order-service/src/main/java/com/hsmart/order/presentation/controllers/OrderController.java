package com.hsmart.order.presentation.controllers;

import com.hsmart.order.application.dto.ApiResponse;
import com.hsmart.order.application.dto.CreateOfferRequestDTO;
import com.hsmart.order.application.dto.CreateOrderRequestDTO;
import com.hsmart.order.application.dto.OfferResponseDTO;
import com.hsmart.order.application.dto.OrderResponseDTO;
import com.hsmart.order.application.dto.OrderStatsResponseDTO;
import com.hsmart.order.application.dto.OrderSummaryDTO;
import com.hsmart.order.application.dto.GhtkWebhookRequestDTO;
import com.hsmart.order.application.dto.PageResponseDTO;
import com.hsmart.order.application.dto.ReturnActionRequestDTO;
import com.hsmart.order.application.dto.ShippingEstimateResponseDTO;
import com.hsmart.order.application.exceptions.MissingUserContextException;
import com.hsmart.order.domain.entities.DeliveryMethod;
import com.hsmart.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Creates an order after the shipping deposit has been paid in payment-service.
     * Internal-only: buyers must go through the deposit payment flow, not create orders directly.
     */
    @PostMapping("/internal/from-deposit")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> createOrderFromDeposit(
            @Valid @RequestBody CreateOrderRequestDTO request,
            @RequestHeader(value = "X-User-Id", required = false) String buyerId
    ) {
        OrderResponseDTO response = orderService.createOrder(request, requireUserId(buyerId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "Order created successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponseDTO<OrderResponseDTO>>> getMyOrders(
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponseDTO<OrderResponseDTO> response = orderService.getOrdersForCurrentUser(requireUserId(currentUserId), pageable);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Orders fetched successfully", response));
    }

    @GetMapping("/shipping-estimate")
    public ResponseEntity<ApiResponse<ShippingEstimateResponseDTO>> estimateShipping(
            @RequestParam Long productId,
            @RequestParam(defaultValue = "VIETTEL_POST") DeliveryMethod deliveryMethod,
            @RequestHeader(value = "X-User-Id", required = false) String buyerId
    ) {
        ShippingEstimateResponseDTO response = orderService.estimateShipping(
                productId,
                deliveryMethod,
                requireUserId(buyerId)
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Shipping estimate calculated successfully", response));
    }

    @GetMapping("/shipping-estimate/guest")
    public ResponseEntity<ApiResponse<ShippingEstimateResponseDTO>> estimateGuestShipping(
            @RequestParam Long productId,
            @RequestParam(defaultValue = "VIETTEL_POST") DeliveryMethod deliveryMethod,
            @RequestParam String province,
            @RequestParam String district
    ) {
        ShippingEstimateResponseDTO response = orderService.estimateGuestShipping(
                productId,
                deliveryMethod,
                province,
                district
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Guest shipping estimate calculated successfully", response));
    }

    @PostMapping("/offers")
    public ResponseEntity<ApiResponse<OfferResponseDTO>> createOffer(
            @Valid @RequestBody CreateOfferRequestDTO request,
            @RequestHeader(value = "X-User-Id", required = false) String buyerId
    ) {
        OfferResponseDTO response = orderService.createOffer(request, requireUserId(buyerId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "Offer submitted successfully", response));
    }

    @GetMapping("/offers")
    public ResponseEntity<ApiResponse<PageResponseDTO<OfferResponseDTO>>> getMyOffers(
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponseDTO<OfferResponseDTO> response = orderService.getOffersForCurrentUser(requireUserId(currentUserId), pageable);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Offers fetched successfully", response));
    }

    @PostMapping("/offers/{id}/accept")
    public ResponseEntity<ApiResponse<OfferResponseDTO>> acceptOffer(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String sellerId
    ) {
        OfferResponseDTO response = orderService.acceptOffer(id, requireUserId(sellerId));
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Offer accepted successfully", response));
    }

    @PostMapping("/offers/{id}/reject")
    public ResponseEntity<ApiResponse<OfferResponseDTO>> rejectOffer(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String sellerId
    ) {
        OfferResponseDTO response = orderService.rejectOffer(id, requireUserId(sellerId));
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Offer rejected successfully", response));
    }

    @PostMapping("/offers/{id}/cancel")
    public ResponseEntity<ApiResponse<OfferResponseDTO>> cancelOffer(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String buyerId
    ) {
        OfferResponseDTO response = orderService.cancelOffer(id, requireUserId(buyerId));
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Offer cancelled successfully", response));
    }

    @PostMapping(value = "/{id}/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<OrderResponseDTO>> confirmOrder(
            @PathVariable Long id,
            @RequestPart(name = "files", required = false) List<MultipartFile> files,
            @RequestPart(name = "file", required = false) MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String sellerId
    ) {
        List<MultipartFile> evidenceImages = normalizeFiles(files, file);
        OrderResponseDTO response = orderService.confirmOrder(id, requireUserId(sellerId), evidenceImages);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Order confirmed successfully", response));
    }

    @PostMapping("/{id}/return-request")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> requestReturn(
            @PathVariable Long id,
            @Valid @RequestBody ReturnActionRequestDTO request,
            @RequestHeader(value = "X-User-Id", required = false) String buyerId
    ) {
        OrderResponseDTO response = orderService.requestReturn(id, requireUserId(buyerId), request.getReason());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Return requested successfully", response));
    }

    @PostMapping("/{id}/return-approve")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> sellerApproveReturn(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String sellerId
    ) {
        OrderResponseDTO response = orderService.sellerApproveReturn(id, requireUserId(sellerId));
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Return approved by seller", response));
    }

    @PostMapping("/{id}/return-reject")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> sellerRejectReturn(
            @PathVariable Long id,
            @Valid @RequestBody ReturnActionRequestDTO request,
            @RequestHeader(value = "X-User-Id", required = false) String sellerId
    ) {
        OrderResponseDTO response = orderService.sellerRejectReturn(id, requireUserId(sellerId), request.getReason());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Return rejected by seller", response));
    }

    @PostMapping("/internal/admin/{orderId}/return-approve")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> adminApproveReturn(@PathVariable Long orderId) {
        OrderResponseDTO response = orderService.adminApproveReturn(orderId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Return approved by admin", response));
    }

    @PostMapping("/internal/admin/{orderId}/return-reject")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> adminRejectReturn(
            @PathVariable Long orderId,
            @Valid @RequestBody ReturnActionRequestDTO request
    ) {
        OrderResponseDTO response = orderService.adminRejectReturn(orderId, request.getReason());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Return rejected by admin", response));
    }

    private List<MultipartFile> normalizeFiles(List<MultipartFile> files, MultipartFile singleFile) {
        List<MultipartFile> normalized = new ArrayList<>();
        if (files != null) {
            files.stream().filter(f -> f != null && !f.isEmpty()).forEach(normalized::add);
        }
        if (singleFile != null && !singleFile.isEmpty()) {
            normalized.add(singleFile);
        }
        return normalized;
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> completeOrder(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String buyerId
    ) {
        OrderResponseDTO response = orderService.completeOrder(id, requireUserId(buyerId));
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Order completed successfully", response));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> cancelOrder(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId
    ) {
        OrderResponseDTO response = orderService.cancelOrder(id, requireUserId(currentUserId));
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Order cancelled successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> getOrder(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId
    ) {
        OrderResponseDTO response = orderService.getOrder(id, requireUserId(currentUserId));
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Order fetched successfully", response));
    }

    /** Internal: order summary for payment-service to drive the seller platform-fee payment. */
    @GetMapping("/internal/{orderId}")
    public ResponseEntity<ApiResponse<OrderSummaryDTO>> getOrderSummary(@PathVariable Long orderId) {
        OrderSummaryDTO response = orderService.getOrderSummary(orderId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Order summary fetched successfully", response));
    }

    /** Internal: flag the order's platform fee as paid (called by payment-service after success). */
    @PostMapping("/internal/{orderId}/platform-fee-paid")
    public ResponseEntity<ApiResponse<Void>> markPlatformFeePaid(@PathVariable Long orderId) {
        orderService.markSellerShippingPaid(orderId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Order flagged as platform-fee paid", null));
    }

    @GetMapping("/internal/stats")
    public ResponseEntity<ApiResponse<OrderStatsResponseDTO>> getInternalStats() {
        OrderStatsResponseDTO response = orderService.getOrderStats();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Order stats fetched successfully", response));
    }

    @GetMapping("/internal/admin/list")
    public ResponseEntity<ApiResponse<PageResponseDTO<OrderResponseDTO>>> listAllOrdersForAdmin(
            @RequestParam(required = false) String status,
            org.springframework.data.domain.Pageable pageable
    ) {
        PageResponseDTO<OrderResponseDTO> response = orderService.listAllOrdersForAdmin(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Orders fetched successfully", response));
    }

    @PostMapping("/internal/admin/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> adminCancelOrder(
            @PathVariable Long orderId
    ) {
        OrderResponseDTO response = orderService.adminCancelOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Order cancelled successfully", response));
    }

    @GetMapping("/internal/latest")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> getLatestOrder(
            @RequestHeader(value = "X-User-Id", required = false) String buyerId
    ) {
        OrderResponseDTO response = orderService.getLatestOrderForBuyer(requireUserId(buyerId));
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Latest order fetched successfully", response));
    }

    @PostMapping("/internal/ghtk-webhook")
    public ResponseEntity<ApiResponse<Void>> handleGhtkWebhook(
            @RequestParam(value = "hash", required = false) String hash,
            @RequestParam MultiValueMap<String, String> payload
    ) {
        log.info("Received GHTK webhook payload: {}", GhtkWebhookRequestDTO.withoutHash(payload));
        orderService.processGhtkWebhook(hash, GhtkWebhookRequestDTO.from(payload));
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "GHTK webhook processed successfully", null));
    }

    private String requireUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new MissingUserContextException();
        }
        return userId.trim();
    }
}
