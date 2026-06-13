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
import com.hsmart.order.application.exceptions.ShippingProviderUnavailableException;
import com.hsmart.order.domain.entities.DeliveryMethod;
import com.hsmart.order.domain.entities.Order;
import com.hsmart.order.domain.entities.OrderStatus;
import com.hsmart.order.infrastructure.config.GhtkProperties;
import com.hsmart.order.infrastructure.messaging.OrderEventPublisher;
import com.hsmart.order.infrastructure.persistence.OrderRepository;
import com.hsmart.order.service.OrderService;
import com.hsmart.order.service.ProductClient;
import com.hsmart.order.service.ShippingProviderClient;
import com.hsmart.order.service.UserClient;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
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

    private static final String APPROVED_STATUS = "APPROVED";
    private static final List<OrderStatus> ACTIVE_ORDER_STATUSES = List.of(OrderStatus.PENDING, OrderStatus.PROCESSING);

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final UserClient userClient;
    private final List<ShippingProviderClient> shippingProviderClients;
    private final OrderEventPublisher orderEventPublisher;
    private final GhtkProperties ghtkProperties;

    @Value("${orders.pending-timeout-minutes:30}")
    private long pendingTimeoutMinutes;

    @Override
    public OrderResponseDTO createOrder(CreateOrderRequestDTO request, String buyerId) {
        ProductResponseDTO product = productClient.getProduct(request.getProductId(), buyerId);
        validateProductCanBeOrdered(product, buyerId);
        ensureProductHasNoActiveOrder(product.id());
        DeliveryMethod deliveryMethod = resolveDeliveryMethod(request);
        ShippingProviderClient shippingProviderClient = resolveShippingProvider(deliveryMethod);
        BigDecimal shippingFee = resolveShippingFee(product.sellerId(), buyerId, shippingProviderClient);

        Order order = Order.builder()
                .buyerId(buyerId)
                .sellerId(product.sellerId())
                .productId(product.id())
                .amount(product.price().add(shippingFee))
                .shippingFee(shippingFee)
                .deliveryMethod(deliveryMethod)
                .status(OrderStatus.PENDING)
                .build();

        Order savedOrder = saveNewOrder(order);
        log.info("Created pending order {} for buyer {} and product {}", savedOrder.getId(), buyerId, product.id());
        return toResponse(savedOrder);
    }

    private Order saveNewOrder(Order order) {
        try {
            return orderRepository.save(order);
        } catch (DataIntegrityViolationException exception) {
            throw new ProductUnavailableException("Product already has an active order");
        }
    }

    private BigDecimal resolveShippingFee(
            String sellerId,
            String buyerId,
            ShippingProviderClient shippingProviderClient
    ) {
        try {
            UserAddressResponseDTO sellerAddress = userClient.getUserAddress(sellerId);
            UserAddressResponseDTO buyerAddress = userClient.getUserAddress(buyerId);
            return shippingProviderClient.calculateShippingFee(sellerAddress, buyerAddress);
        } catch (RuntimeException exception) {
            log.warn(
                    "Shipping fee calculation failed for seller {} and buyer {} through {}",
                    sellerId,
                    buyerId,
                    shippingProviderClient.deliveryMethod(),
                    exception
            );
            throw new ShippingProviderUnavailableException("Shipping fee calculation failed", exception);
        }
    }

    @Override
    public OrderResponseDTO confirmOrder(Long orderId, String sellerId) {
        Order order = findOrder(orderId);
        order = expirePendingOrderIfNeeded(order);
        if (!order.getSellerId().equals(sellerId)) {
            throw new OrderStateException("Only the seller can confirm this order");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderStateException("Only pending orders can be confirmed");
        }

        ProductResponseDTO product = productClient.getProduct(order.getProductId(), sellerId);
        UserAddressResponseDTO sellerAddress = userClient.getUserAddress(order.getSellerId());
        UserAddressResponseDTO buyerAddress = userClient.getUserAddress(order.getBuyerId());
        ShippingProviderClient shippingProviderClient = resolveShippingProvider(order.getDeliveryMethod());

        try {
            String trackingCode = shippingProviderClient.createShipment(new GhtkShipmentRequestDTO(
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
            log.info(
                    "Confirmed order {} with {} tracking code {}",
                    savedOrder.getId(),
                    shippingProviderClient.deliveryMethod(),
                    trackingCode
            );
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
        order = expirePendingOrderIfNeeded(order);

        if (!order.getBuyerId().equals(buyerId)) {
            throw new OrderStateException("Only the buyer can complete this order");
        }

        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new OrderStateException("Only processing orders can be completed");
        }

        return markOrderCompleted(order);
    }

    @Override
    public OrderResponseDTO cancelOrder(Long orderId, String currentUserId) {
        Order order = findOrder(orderId);
        order = expirePendingOrderIfNeeded(order);
        if (!order.getBuyerId().equals(currentUserId) && !order.getSellerId().equals(currentUserId)) {
            throw new OrderStateException("Only the buyer or seller can cancel this order");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderStateException("Only pending orders can be cancelled");
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);
        log.info("Cancelled pending order {} by user {}", savedOrder.getId(), currentUserId);
        return toResponse(savedOrder);
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
    public List<OrderResponseDTO> getOrdersForCurrentUser(String currentUserId) {
        return orderRepository.findByBuyerIdOrSellerIdOrderByCreatedAtDescIdDesc(currentUserId, currentUserId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDTO getOrder(Long orderId, String currentUserId) {
        Order order = orderRepository.findByIdAndBuyerIdOrIdAndSellerId(orderId, currentUserId, orderId, currentUserId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return toResponse(order);
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
        return APPROVED_STATUS.equalsIgnoreCase(status);
    }

    private void ensureProductHasNoActiveOrder(Long productId) {
        if (orderRepository.existsByProductIdAndStatusIn(productId, ACTIVE_ORDER_STATUSES)) {
            throw new ProductUnavailableException("Product already has an active order");
        }
    }

    private DeliveryMethod resolveDeliveryMethod(CreateOrderRequestDTO request) {
        return request.getDeliveryMethod() != null ? request.getDeliveryMethod() : DeliveryMethod.GHTK;
    }

    private ShippingProviderClient resolveShippingProvider(DeliveryMethod deliveryMethod) {
        DeliveryMethod resolvedDeliveryMethod = deliveryMethod != null ? deliveryMethod : DeliveryMethod.GHTK;
        return shippingProviderClients.stream()
                .filter(client -> client.deliveryMethod() == resolvedDeliveryMethod)
                .findFirst()
                .orElseThrow(() -> new OrderStateException("Unsupported delivery method"));
    }

    private Order expirePendingOrderIfNeeded(Order order) {
        if (order.getStatus() != OrderStatus.PENDING || order.getCreatedAt() == null || pendingTimeoutMinutes <= 0) {
            return order;
        }
        if (!order.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(pendingTimeoutMinutes))) {
            return order;
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);
        log.info("Cancelled expired pending order {}", savedOrder.getId());
        return savedOrder;
    }

    @Scheduled(fixedDelayString = "${orders.pending-timeout-scan-ms:60000}")
    public void cancelExpiredPendingOrders() {
        if (pendingTimeoutMinutes <= 0) {
            return;
        }
        LocalDateTime expiredBefore = LocalDateTime.now().minusMinutes(pendingTimeoutMinutes);
        int cancelledCount = orderRepository.cancelExpiredPendingOrders(expiredBefore);
        if (cancelledCount > 0) {
            log.info("Cancelled {} expired pending orders", cancelledCount);
        }
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
                .deliveryMethod(order.getDeliveryMethod())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
