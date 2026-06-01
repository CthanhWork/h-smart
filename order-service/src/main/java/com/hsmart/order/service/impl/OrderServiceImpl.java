package com.hsmart.order.service.impl;

import com.hsmart.order.application.dto.CreateOrderRequestDTO;
import com.hsmart.order.application.dto.OrderCompletedEvent;
import com.hsmart.order.application.dto.OrderResponseDTO;
import com.hsmart.order.application.dto.OrderStatsResponseDTO;
import com.hsmart.order.application.dto.ProductResponseDTO;
import com.hsmart.order.application.dto.GhtkShipmentRequestDTO;
import com.hsmart.order.application.dto.GhtkWebhookRequestDTO;
import com.hsmart.order.application.dto.UserAddressResponseDTO;
import com.hsmart.order.application.exceptions.InvalidGhtkWebhookException;
import com.hsmart.order.application.exceptions.OrderNotFoundException;
import com.hsmart.order.application.exceptions.OrderStateException;
import com.hsmart.order.application.exceptions.ProductUnavailableException;
import com.hsmart.order.domain.entities.Order;
import com.hsmart.order.domain.entities.OrderStatus;
import com.hsmart.order.infrastructure.config.GhtkProperties;
import com.hsmart.order.infrastructure.messaging.OrderEventPublisher;
import com.hsmart.order.infrastructure.persistence.OrderRepository;
import com.hsmart.order.service.OrderService;
import com.hsmart.order.service.ProductClient;
import com.hsmart.order.service.GhtkClient;
import com.hsmart.order.service.UserClient;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    private static final String APPROVED_STATUS = "APPROVED";

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final UserClient userClient;
    private final GhtkClient ghtkClient;
    private final OrderEventPublisher orderEventPublisher;
    private final GhtkProperties ghtkProperties;

    @Override
    public OrderResponseDTO createOrder(CreateOrderRequestDTO request, String buyerId) {
        ProductResponseDTO product = productClient.getProduct(request.getProductId(), buyerId);
        validateProductCanBeOrdered(product, buyerId);
        BigDecimal shippingFee = resolveShippingFee(product.sellerId(), buyerId);

        Order order = Order.builder()
                .buyerId(buyerId)
                .sellerId(product.sellerId())
                .productId(product.id())
                .amount(product.price().add(shippingFee))
                .shippingFee(shippingFee)
                .status(OrderStatus.PENDING)
                .build();

        Order savedOrder = orderRepository.save(order);
        log.info("Created pending order {} for buyer {} and product {}", savedOrder.getId(), buyerId, product.id());
        return toResponse(savedOrder);
    }

    private BigDecimal resolveShippingFee(String sellerId, String buyerId) {
        try {
            UserAddressResponseDTO sellerAddress = userClient.getUserAddress(sellerId);
            UserAddressResponseDTO buyerAddress = userClient.getUserAddress(buyerId);
            return ghtkClient.calculateShippingFee(sellerAddress, buyerAddress);
        } catch (RuntimeException exception) {
            log.warn(
                    "Shipping fee calculation failed for seller {} and buyer {}. Defaulting shipping fee to zero",
                    sellerId,
                    buyerId,
                    exception
            );
            return BigDecimal.ZERO;
        }
    }

    @Override
    public OrderResponseDTO confirmOrder(Long orderId, String sellerId) {
        Order order = findOrder(orderId);
        if (!order.getSellerId().equals(sellerId)) {
            throw new OrderStateException("Only the seller can confirm this order");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderStateException("Only pending orders can be confirmed");
        }

        ProductResponseDTO product = productClient.getProduct(order.getProductId(), sellerId);
        UserAddressResponseDTO sellerAddress = userClient.getUserAddress(order.getSellerId());
        UserAddressResponseDTO buyerAddress = userClient.getUserAddress(order.getBuyerId());

        try {
            String trackingCode = ghtkClient.createShipment(new GhtkShipmentRequestDTO(
                    order.getId().toString(),
                    product.title(),
                    product.price(),
                    order.getAmount(),
                    sellerAddress,
                    buyerAddress
            ));
            order.setTrackingCode(trackingCode);
            order.setStatus(OrderStatus.PROCESSING);
            Order savedOrder = orderRepository.save(order);
            log.info("Confirmed order {} with GHTK tracking code {}", savedOrder.getId(), trackingCode);
            return toResponse(savedOrder);
        } catch (IllegalArgumentException exception) {
            throw new OrderStateException(exception.getMessage());
        }
    }

    @Override
    public void processGhtkWebhook(String hash, GhtkWebhookRequestDTO request) {
        validateWebhook(hash, request);
        if (request.statusId() != 5) {
            log.info("Ignoring GHTK webhook for tracking code {} with status {}", request.labelId(), request.statusId());
            return;
        }

        Order order = orderRepository.findByTrackingCode(request.labelId())
                .orElseThrow(() -> new OrderNotFoundException("Order not found for tracking code " + request.labelId()));
        if (order.getStatus() == OrderStatus.COMPLETED) {
            log.info("Ignoring duplicate completed GHTK webhook for order {}", order.getId());
            return;
        }
        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new OrderStateException("Only processing orders can be completed by GHTK webhook");
        }

        markOrderCompleted(order);
    }

    @Override
    public OrderResponseDTO completeOrder(Long orderId, String buyerId) {
        Order order = findOrder(orderId);

        if (!order.getBuyerId().equals(buyerId)) {
            throw new OrderStateException("Only the buyer can complete this order");
        }

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PROCESSING) {
            throw new OrderStateException("Only pending or processing orders can be completed");
        }

        return markOrderCompleted(order);
    }

    private OrderResponseDTO markOrderCompleted(Order order) {
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
        return toResponse(findOrder(orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDTO getLatestOrderForBuyer(String buyerId) {
        Order order = orderRepository.findFirstByBuyerIdOrderByCreatedAtDescIdDesc(buyerId)
                .orElseThrow(() -> new OrderNotFoundException("No orders found for buyer " + buyerId));
        return toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderStatsResponseDTO getOrderStats() {
        return OrderStatsResponseDTO.builder()
                .completedOrderCount(orderRepository.countByStatus(OrderStatus.COMPLETED))
                .totalCompletedRevenue(orderRepository.sumAmountByStatus(OrderStatus.COMPLETED))
                .build();
    }

    private void validateProductCanBeOrdered(ProductResponseDTO product, String buyerId) {
        if (product.id() == null || !isOrderableStatus(product.status())) {
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

    private boolean isOrderableStatus(String status) {
        return ACTIVE_STATUS.equalsIgnoreCase(status) || APPROVED_STATUS.equalsIgnoreCase(status);
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private void validateWebhook(String hash, GhtkWebhookRequestDTO request) {
        if (!StringUtils.hasText(ghtkProperties.webhookHash())
                || !StringUtils.hasText(hash)
                || !MessageDigest.isEqual(
                ghtkProperties.webhookHash().getBytes(StandardCharsets.UTF_8),
                hash.trim().getBytes(StandardCharsets.UTF_8)
        )) {
            throw new InvalidGhtkWebhookException("Invalid GHTK webhook credentials");
        }
        if (request == null || !StringUtils.hasText(request.labelId()) || request.statusId() == null) {
            throw new InvalidGhtkWebhookException("Invalid GHTK webhook payload");
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
                .shippingFee(order.getShippingFee())
                .trackingCode(order.getTrackingCode())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
