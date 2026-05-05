package com.hsmart.order.service.impl;

import com.hsmart.order.application.dto.CreateOrderRequestDTO;
import com.hsmart.order.application.dto.OrderCompletedEvent;
import com.hsmart.order.application.dto.OrderResponseDTO;
import com.hsmart.order.application.dto.ProductResponseDTO;
import com.hsmart.order.application.exceptions.OrderNotFoundException;
import com.hsmart.order.application.exceptions.OrderStateException;
import com.hsmart.order.application.exceptions.ProductUnavailableException;
import com.hsmart.order.domain.entities.Order;
import com.hsmart.order.domain.entities.OrderStatus;
import com.hsmart.order.infrastructure.messaging.OrderEventPublisher;
import com.hsmart.order.infrastructure.persistence.OrderRepository;
import com.hsmart.order.service.OrderService;
import com.hsmart.order.service.ProductClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderServiceImpl implements OrderService {

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final OrderEventPublisher orderEventPublisher;

    @Override
    public OrderResponseDTO createOrder(CreateOrderRequestDTO request, String buyerId) {
        ProductResponseDTO product = productClient.getProduct(request.getProductId(), buyerId);
        validateProductCanBeOrdered(product, buyerId);

        Order order = Order.builder()
                .buyerId(buyerId)
                .sellerId(product.sellerId())
                .productId(product.id())
                .amount(product.price())
                .status(OrderStatus.PENDING)
                .build();

        Order savedOrder = orderRepository.save(order);
        log.info("Created pending order {} for buyer {} and product {}", savedOrder.getId(), buyerId, product.id());
        return toResponse(savedOrder);
    }

    @Override
    public OrderResponseDTO completeOrder(Long orderId, String buyerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getBuyerId().equals(buyerId)) {
            throw new OrderStateException("Only the buyer can complete this order");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderStateException("Only pending orders can be completed");
        }

        order.setStatus(OrderStatus.COMPLETED);
        Order savedOrder = orderRepository.save(order);

        OrderCompletedEvent event = OrderCompletedEvent.builder()
                .productId(savedOrder.getProductId())
                .build();
        publishAfterCommit(() -> orderEventPublisher.publishOrderCompleted(event));

        log.info("Completed order {} for product {}", savedOrder.getId(), savedOrder.getProductId());
        return toResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDTO getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return toResponse(order);
    }

    private void validateProductCanBeOrdered(ProductResponseDTO product, String buyerId) {
        if (product.id() == null || !ACTIVE_STATUS.equalsIgnoreCase(product.status())) {
            throw new ProductUnavailableException("Product is not available for ordering");
        }

        if (!StringUtils.hasText(product.sellerId())) {
            throw new ProductUnavailableException("Product seller information is missing");
        }

        if (product.sellerId().equals(buyerId)) {
            throw new ProductUnavailableException("Buyers cannot order their own products");
        }

        if (product.price() == null) {
            throw new ProductUnavailableException("Product price is missing");
        }
    }

    private void publishAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private OrderResponseDTO toResponse(Order order) {
        return OrderResponseDTO.builder()
                .id(order.getId())
                .buyerId(order.getBuyerId())
                .sellerId(order.getSellerId())
                .productId(order.getProductId())
                .amount(order.getAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
