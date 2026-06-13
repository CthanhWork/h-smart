package com.hsmart.order.presentation.controllers;

import com.hsmart.order.application.dto.ApiResponse;
import com.hsmart.order.application.dto.CreateOrderRequestDTO;
import com.hsmart.order.application.dto.OrderResponseDTO;
import com.hsmart.order.application.dto.OrderStatsResponseDTO;
import com.hsmart.order.application.dto.GhtkWebhookRequestDTO;
import com.hsmart.order.application.exceptions.MissingUserContextException;
import com.hsmart.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponseDTO>> createOrder(
            @Valid @RequestBody CreateOrderRequestDTO request,
            @RequestHeader(value = "X-User-Id", required = false) String buyerId
    ) {
        OrderResponseDTO response = orderService.createOrder(request, requireUserId(buyerId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "Order created successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponseDTO>>> getMyOrders(
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId
    ) {
        List<OrderResponseDTO> response = orderService.getOrdersForCurrentUser(requireUserId(currentUserId));
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Orders fetched successfully", response));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> confirmOrder(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String sellerId
    ) {
        OrderResponseDTO response = orderService.confirmOrder(id, requireUserId(sellerId));
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Order confirmed successfully", response));
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

    @GetMapping("/internal/stats")
    public ResponseEntity<ApiResponse<OrderStatsResponseDTO>> getInternalStats() {
        OrderStatsResponseDTO response = orderService.getOrderStats();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Order stats fetched successfully", response));
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
