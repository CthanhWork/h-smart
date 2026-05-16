package com.hsmart.order.presentation.controllers;

import com.hsmart.order.application.dto.ApiResponse;
import com.hsmart.order.application.dto.CreateOrderRequestDTO;
import com.hsmart.order.application.dto.OrderResponseDTO;
import com.hsmart.order.application.dto.OrderStatsResponseDTO;
import com.hsmart.order.application.exceptions.MissingUserContextException;
import com.hsmart.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> completeOrder(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String buyerId
    ) {
        OrderResponseDTO response = orderService.completeOrder(id, requireUserId(buyerId));
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Order completed successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> getOrder(@PathVariable Long id) {
        OrderResponseDTO response = orderService.getOrder(id);
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

    private String requireUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new MissingUserContextException();
        }
        return userId.trim();
    }
}
