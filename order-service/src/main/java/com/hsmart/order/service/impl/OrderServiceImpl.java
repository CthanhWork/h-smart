package com.hsmart.order.service.impl;

import com.hsmart.order.application.dto.CreateOrderRequestDTO;
import com.hsmart.order.application.dto.CreateOfferRequestDTO;
import com.hsmart.order.application.dto.OrderCompletedEvent;
import com.hsmart.order.application.dto.OfferResponseDTO;
import com.hsmart.order.application.dto.OrderResponseDTO;
import com.hsmart.order.application.dto.OrderStatsResponseDTO;
import com.hsmart.order.application.dto.OrderSummaryDTO;
import com.hsmart.order.application.dto.PageResponseDTO;
import com.hsmart.order.application.dto.ProductResponseDTO;
import com.hsmart.order.application.dto.GhtkShipmentRequestDTO;
import com.hsmart.order.application.dto.GhtkWebhookRequestDTO;
import com.hsmart.order.application.dto.ShippingEstimateResponseDTO;
import com.hsmart.order.application.dto.UserAddressResponseDTO;
import com.hsmart.order.application.exceptions.InvalidGhtkWebhookException;
import com.hsmart.order.application.exceptions.OrderNotFoundException;
import com.hsmart.order.application.exceptions.OrderStateException;
import com.hsmart.order.application.exceptions.ProductUnavailableException;
import com.hsmart.order.application.exceptions.ShippingProviderUnavailableException;
import com.hsmart.order.domain.entities.DeliveryMethod;
import com.hsmart.order.domain.entities.OfferStatus;
import com.hsmart.order.domain.entities.Order;
import com.hsmart.order.domain.entities.OrderStatus;
import com.hsmart.order.domain.entities.ProductOffer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.order.infrastructure.config.ApplicationProperties;
import com.hsmart.order.infrastructure.config.GhtkProperties;
import com.hsmart.order.infrastructure.config.StorageProperties;
import com.hsmart.order.infrastructure.messaging.OrderEventPublisher;
import com.hsmart.order.infrastructure.persistence.OrderRepository;
import com.hsmart.order.infrastructure.persistence.ProductOfferRepository;
import com.hsmart.order.infrastructure.validation.FileValidator;
import com.hsmart.order.service.NotificationClient;
import com.hsmart.order.service.OrderService;
import com.hsmart.order.service.PaymentClient;
import com.hsmart.order.service.ProductClient;
import com.hsmart.order.service.ShippingProviderClient;
import com.hsmart.order.service.UserClient;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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
    private static final List<OfferStatus> ACTIVE_OFFER_STATUSES = List.of(OfferStatus.PENDING, OfferStatus.ACCEPTED);
    private static final int OFFER_EXPIRATION_HOURS = 24;

    private final OrderRepository orderRepository;
    private final ProductOfferRepository productOfferRepository;
    private final ProductClient productClient;
    private final UserClient userClient;
    private final List<ShippingProviderClient> shippingProviderClients;
    private final OrderEventPublisher orderEventPublisher;
    private final GhtkProperties ghtkProperties;
    private final NotificationClient notificationClient;
    private final PaymentClient paymentClient;
    private final StorageProperties storageProperties;
    private final ApplicationProperties applicationProperties;
    private final ObjectMapper objectMapper;
    private final FileValidator fileValidator;

    @Value("${orders.pending-timeout-minutes:30}")
    private long pendingTimeoutMinutes;

    @Value("${orders.return-window-days:3}")
    private long returnWindowDays;

    @Value("${orders.return-approval-timeout-hours:48}")
    private long returnApprovalTimeoutHours;

    @Value("${offers.resubmit-cooldown-hours:1}")
    private int offerResubmitCooldownHours;

    @Value("${platform.fee.max-rate:0.10}")
    private double platformFeeMaxRate;

    @Override
    public OrderResponseDTO createOrder(CreateOrderRequestDTO request, String buyerId) {
        ProductResponseDTO product = productClient.getProduct(request.getProductId(), buyerId);
        validateProductCanBeOrdered(product, buyerId);
        ensureProductHasNoActiveOrder(product.id());
        DeliveryMethod deliveryMethod = resolveDeliveryMethod(request);
        ShippingProviderClient shippingProviderClient = resolveShippingProvider(deliveryMethod);
        BigDecimal shippingFee = resolveShippingFee(product.sellerId(), buyerId, shippingProviderClient);
        ProductOffer checkoutOffer = resolveCheckoutOffer(request.getOfferId(), product, buyerId);
        BigDecimal productAmount = checkoutOffer != null ? checkoutOffer.getOfferPrice() : product.price();
        BigDecimal platformFee = computePlatformFee(productAmount, shippingFee);

        Order order = Order.builder()
                .buyerId(buyerId)
                .sellerId(product.sellerId())
                .productId(product.id())
                .productTitle(product.title())
                .productImageUrl(product.imageUrl())
                .amount(productAmount.add(shippingFee))
                .productAmount(productAmount)
                .shippingFee(shippingFee)
                .platformFee(platformFee)
                .deliveryMethod(deliveryMethod)
                .status(OrderStatus.PENDING)
                .build();

        Order savedOrder = saveNewOrder(order);
        if (checkoutOffer != null) {
            checkoutOffer.setStatus(OfferStatus.ORDERED);
            productOfferRepository.save(checkoutOffer);
        }
        cancelCompetingOffers(product.id(), checkoutOffer);
        log.info("Created pending order {} for buyer {} and product {}", savedOrder.getId(), buyerId, product.id());
        return toResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public ShippingEstimateResponseDTO estimateShipping(Long productId, DeliveryMethod deliveryMethod, String buyerId) {
        ProductResponseDTO product = productClient.getProduct(productId, buyerId);
        validateProductCanBeOrdered(product, buyerId);
        ShippingProviderClient shippingProviderClient = resolveShippingProvider(deliveryMethod);
        UserAddressResponseDTO sellerAddress = userClient.getUserAddress(product.sellerId());
        UserAddressResponseDTO buyerAddress = userClient.getUserAddress(buyerId);
        BigDecimal shippingFee = calculateShippingFee(product.sellerId(), buyerId, shippingProviderClient, sellerAddress, buyerAddress);
        return ShippingEstimateResponseDTO.builder()
                .productId(product.id())
                .deliveryMethod(shippingProviderClient.deliveryMethod())
                .shippingFee(shippingFee)
                .productPrice(product.price())
                .estimatedTotal(product.price().add(shippingFee))
                .sellerDistrict(sellerAddress.district())
                .sellerProvince(sellerAddress.province())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ShippingEstimateResponseDTO estimateGuestShipping(
            Long productId,
            DeliveryMethod deliveryMethod,
            String province,
            String district
    ) {
        if (!StringUtils.hasText(province) || !StringUtils.hasText(district)) {
            throw new OrderStateException("Province and district are required for guest shipping estimate");
        }

        ProductResponseDTO product = productClient.getProduct(productId, "guest");
        validateProductCanBeOrdered(product, "guest");
        ShippingProviderClient shippingProviderClient = resolveShippingProvider(deliveryMethod);
        UserAddressResponseDTO sellerAddress = userClient.getUserAddress(product.sellerId());
        UserAddressResponseDTO guestAddress = new UserAddressResponseDTO(
                "guest",
                "Guest buyer",
                "0900000000",
                province.trim(),
                district.trim(),
                "Unknown ward",
                "Guest address"
        );
        BigDecimal shippingFee = calculateShippingFee(
                product.sellerId(),
                "guest",
                shippingProviderClient,
                sellerAddress,
                guestAddress
        );
        return ShippingEstimateResponseDTO.builder()
                .productId(product.id())
                .deliveryMethod(shippingProviderClient.deliveryMethod())
                .shippingFee(shippingFee)
                .productPrice(product.price())
                .estimatedTotal(product.price().add(shippingFee))
                .sellerDistrict(sellerAddress.district())
                .sellerProvince(sellerAddress.province())
                .build();
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
        UserAddressResponseDTO sellerAddress = userClient.getUserAddress(sellerId);
        UserAddressResponseDTO buyerAddress = userClient.getUserAddress(buyerId);
        return calculateShippingFee(sellerId, buyerId, shippingProviderClient, sellerAddress, buyerAddress);
    }

    private BigDecimal calculateShippingFee(
            String sellerId,
            String buyerId,
            ShippingProviderClient shippingProviderClient,
            UserAddressResponseDTO sellerAddress,
            UserAddressResponseDTO buyerAddress
    ) {
        try {
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

    /**
     * Platform fee the seller pays = the shipping fee, capped at {@code platformFeeMaxRate} of the
     * product amount (the order value, excluding shipping). Rounded to 2 decimals, never negative.
     */
    private BigDecimal computePlatformFee(BigDecimal productAmount, BigDecimal shippingFee) {
        if (shippingFee == null || shippingFee.signum() <= 0 || productAmount == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal cap = productAmount
                .multiply(BigDecimal.valueOf(platformFeeMaxRate))
                .setScale(2, RoundingMode.HALF_UP);
        return shippingFee.min(cap).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public OrderResponseDTO confirmOrder(Long orderId, String sellerId, List<MultipartFile> evidenceImages) {
        Order order = findOrder(orderId);
        order = expirePendingOrderIfNeeded(order);
        if (!order.getSellerId().equals(sellerId)) {
            throw new OrderStateException("Only the seller can confirm this order");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderStateException("Only pending orders can be confirmed");
        }
        if (!order.isSellerShippingFeePaid()) {
            throw new OrderStateException("Bạn cần thanh toán phí nền tảng trước khi xác nhận đơn hàng");
        }

        List<MultipartFile> normalizedImages = normalizeImages(evidenceImages);
        fileValidator.validateImages(normalizedImages); // Add validation
        ProductResponseDTO product = productClient.getProduct(order.getProductId(), sellerId);
        UserAddressResponseDTO sellerAddress = userClient.getUserAddress(order.getSellerId());
        UserAddressResponseDTO buyerAddress = userClient.getUserAddress(order.getBuyerId());
        ShippingProviderClient shippingProviderClient = resolveShippingProvider(order.getDeliveryMethod());

        BigDecimal productAmount = resolveProductAmount(order, product.price());
        try {
            String trackingCode = shippingProviderClient.createShipment(new GhtkShipmentRequestDTO(
                    order.getId().toString(),
                    product.title(),
                    productAmount,
                    productAmount,
                    sellerAddress,
                    buyerAddress
            ));
            // Lưu ảnh minh chứng chất lượng hàng trước khi gửi (bằng chứng cho đổi/trả sau này).
            order.setEvidenceImages(writeJson(saveUploadedFiles(normalizedImages)));
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
        boolean isBuyer = order.getBuyerId().equals(currentUserId);
        boolean isSeller = order.getSellerId().equals(currentUserId);
        if (!isBuyer && !isSeller) {
            throw new OrderStateException("Only the buyer or seller can cancel this order");
        }
        if (order.getStatus() == OrderStatus.PENDING) {
            // both buyer and seller can cancel pending orders
        } else if (order.getStatus() == OrderStatus.PROCESSING && isSeller) {
            log.warn("Seller {} cancelling processing order {} with tracking code {}; manual carrier cancellation required",
                    currentUserId, orderId, order.getTrackingCode());
        } else {
            throw new OrderStateException("Buyers can only cancel pending orders; sellers can cancel pending or processing orders");
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);
        publishAfterCommit(() -> paymentClient.refundDeposit(savedOrder.getId()));
        log.info("Cancelled order {} by user {}", savedOrder.getId(), currentUserId);
        return toResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderSummaryDTO getOrderSummary(Long orderId) {
        Order order = findOrder(orderId);
        return OrderSummaryDTO.builder()
                .id(order.getId())
                .sellerId(order.getSellerId())
                .productId(order.getProductId())
                .productAmount(resolveProductAmount(order, BigDecimal.ZERO))
                .shippingFee(order.getShippingFee())
                .platformFee(order.getPlatformFee())
                .status(order.getStatus().name())
                .sellerShippingFeePaid(order.isSellerShippingFeePaid())
                .build();
    }

    @Override
    public void markSellerShippingPaid(Long orderId) {
        Order order = findOrder(orderId);
        if (order.isSellerShippingFeePaid()) {
            log.info("Order {} already flagged as platform-fee paid; skipping", orderId);
            return;
        }
        order.setSellerShippingFeePaid(true);
        orderRepository.save(order);
        log.info("Flagged order {} as platform-fee paid", orderId);
    }

    private OrderResponseDTO markOrderCompleted(Order order) {
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);

        OrderCompletedEvent event = OrderCompletedEvent.builder()
                .productId(savedOrder.getProductId())
                .build();
        publishAfterCommit(() -> orderEventPublisher.publishOrderCompleted(event));
        publishAfterCommit(() -> paymentClient.settleDeposit(savedOrder.getId()));

        log.info("Completed order {} for product {}", savedOrder.getId(), savedOrder.getProductId());
        return toResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<OrderResponseDTO> getOrdersForCurrentUser(String currentUserId, Pageable pageable) {
        Page<OrderResponseDTO> page = orderRepository
                .findByBuyerIdOrSellerIdOrderByCreatedAtDescIdDesc(currentUserId, currentUserId, pageable)
                .map(this::toResponse);
        return PageResponseDTO.from(page);
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

    @Override
    public OfferResponseDTO createOffer(CreateOfferRequestDTO request, String buyerId) {
        ProductResponseDTO product = productClient.getProduct(request.getProductId(), buyerId);
        validateProductCanBeOrdered(product, buyerId);
        if (!product.negotiable()) {
            throw new OrderStateException("This product is not open to offers");
        }
        if (productOfferRepository.existsByProductIdAndBuyerIdAndStatusIn(product.id(), buyerId, ACTIVE_OFFER_STATUSES)) {
            throw new OrderStateException("Buyer already has an active offer for this product");
        }
        if (offerResubmitCooldownHours > 0
                && productOfferRepository.existsByProductIdAndBuyerIdAndCreatedAtAfter(
                        product.id(), buyerId, LocalDateTime.now().minusHours(offerResubmitCooldownHours))) {
            throw new OrderStateException("You can only submit one offer per product per " + offerResubmitCooldownHours + " hour(s)");
        }

        int discountPercent = request.getDiscountPercent();
        BigDecimal offerPrice = product.price()
                .multiply(BigDecimal.valueOf(100L - discountPercent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        if (product.minPrice() != null && offerPrice.compareTo(product.minPrice()) < 0) {
            throw new OrderStateException("Offer price is below the seller's minimum acceptable price");
        }

        ProductOffer offer = ProductOffer.builder()
                .productId(product.id())
                .buyerId(buyerId)
                .sellerId(product.sellerId())
                .originalPrice(product.price())
                .offerPrice(offerPrice)
                .discountPercent(discountPercent)
                .status(OfferStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusHours(OFFER_EXPIRATION_HOURS))
                .build();

        ProductOffer savedOffer = productOfferRepository.save(offer);
        log.info(
                "Created offer {} for product {} from buyer {} to seller {}",
                savedOffer.getId(),
                savedOffer.getProductId(),
                savedOffer.getBuyerId(),
                savedOffer.getSellerId()
        );
        OfferResponseDTO response = toOfferResponse(savedOffer);
        publishAfterCommit(() -> notificationClient.sendOfferNotification(response));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<OfferResponseDTO> getOffersForCurrentUser(String currentUserId, Pageable pageable) {
        Page<OfferResponseDTO> page = productOfferRepository
                .findByBuyerIdOrSellerIdOrderByCreatedAtDescIdDesc(currentUserId, currentUserId, pageable)
                .map(this::toOfferResponse);
        return PageResponseDTO.from(page);
    }

    @Override
    public OfferResponseDTO acceptOffer(Long offerId, String sellerId) {
        ProductOffer offer = expireOfferIfNeeded(findOffer(offerId));
        if (!offer.getSellerId().equals(sellerId)) {
            throw new OrderStateException("Only the seller can accept this offer");
        }
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new OrderStateException("Only pending offers can be accepted");
        }

        try {
            offer.setStatus(OfferStatus.ACCEPTED);
            ProductOffer savedOffer = productOfferRepository.save(offer);
            OfferResponseDTO response = toOfferResponse(savedOffer);
            publishAfterCommit(() -> notificationClient.sendOfferAcceptedNotification(response));
            log.info("Accepted offer {} for product {}", savedOffer.getId(), savedOffer.getProductId());
            return response;
        } catch (OptimisticLockingFailureException ex) {
            throw new OrderStateException("This offer was just modified by another operation. Please refresh and try again.");
        }
    }

    @Override
    public OfferResponseDTO rejectOffer(Long offerId, String sellerId) {
        ProductOffer offer = expireOfferIfNeeded(findOffer(offerId));
        if (!offer.getSellerId().equals(sellerId)) {
            throw new OrderStateException("Only the seller can reject this offer");
        }
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new OrderStateException("Only pending offers can be rejected");
        }

        try {
            offer.setStatus(OfferStatus.REJECTED);
            ProductOffer savedOffer = productOfferRepository.save(offer);
            OfferResponseDTO response = toOfferResponse(savedOffer);
            publishAfterCommit(() -> notificationClient.sendOfferRejectedNotification(response));
            log.info("Rejected offer {} for product {}", savedOffer.getId(), savedOffer.getProductId());
            return response;
        } catch (OptimisticLockingFailureException ex) {
            throw new OrderStateException("This offer was just modified by another operation. Please refresh and try again.");
        }
    }

    @Override
    public OfferResponseDTO cancelOffer(Long offerId, String buyerId) {
        ProductOffer offer = expireOfferIfNeeded(findOffer(offerId));
        if (!offer.getBuyerId().equals(buyerId)) {
            throw new OrderStateException("Only the buyer can cancel this offer");
        }
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new OrderStateException("Only pending offers can be cancelled");
        }

        offer.setStatus(OfferStatus.CANCELLED);
        ProductOffer savedOffer = productOfferRepository.save(offer);
        OfferResponseDTO response = toOfferResponse(savedOffer);
        publishAfterCommit(() -> notificationClient.sendOfferCancelledNotification(response));
        log.info("Cancelled offer {} for product {}", savedOffer.getId(), savedOffer.getProductId());
        return response;
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
        return APPROVED_STATUS.equalsIgnoreCase(status) || "ACTIVE".equalsIgnoreCase(status);
    }

    private void ensureProductHasNoActiveOrder(Long productId) {
        if (orderRepository.existsByProductIdAndStatusIn(productId, ACTIVE_ORDER_STATUSES)) {
            throw new ProductUnavailableException("Product already has an active order");
        }
    }

    private DeliveryMethod resolveDeliveryMethod(CreateOrderRequestDTO request) {
        return request.getDeliveryMethod() != null ? request.getDeliveryMethod() : DeliveryMethod.VIETTEL_POST;
    }

    private ShippingProviderClient resolveShippingProvider(DeliveryMethod deliveryMethod) {
        DeliveryMethod resolvedDeliveryMethod = deliveryMethod != null ? deliveryMethod : DeliveryMethod.VIETTEL_POST;
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
        publishAfterCommit(() -> paymentClient.refundDeposit(savedOrder.getId()));
        log.info("Cancelled expired pending order {}", savedOrder.getId());
        return savedOrder;
    }

    @Scheduled(fixedDelayString = "${orders.pending-timeout-scan-ms:60000}")
    public void cancelExpiredPendingOrders() {
        if (pendingTimeoutMinutes <= 0) {
            return;
        }
        LocalDateTime expiredBefore = LocalDateTime.now().minusMinutes(pendingTimeoutMinutes);
        List<Order> expiredOrders = orderRepository.findPendingOrdersExpiredBefore(expiredBefore);
        if (expiredOrders.isEmpty()) {
            return;
        }
        expiredOrders.forEach(order -> order.setStatus(OrderStatus.CANCELLED));
        List<Order> saved = orderRepository.saveAll(expiredOrders);
        saved.forEach(order -> {
            OrderResponseDTO response = toResponse(order);
            publishAfterCommit(() -> notificationClient.sendOrderCancelledNotification(response));
            publishAfterCommit(() -> paymentClient.refundDeposit(order.getId()));
        });
        log.info("Cancelled {} expired pending orders", saved.size());
    }

    @Scheduled(fixedDelayString = "${offers.expiration-scan-ms:300000}")
    public void expirePendingOffers() {
        List<ProductOffer> expiredOffers = productOfferRepository.findPendingExpiredOffers(LocalDateTime.now());
        if (expiredOffers.isEmpty()) {
            return;
        }
        expiredOffers.forEach(offer -> offer.setStatus(OfferStatus.EXPIRED));
        List<ProductOffer> saved = productOfferRepository.saveAll(expiredOffers);
        saved.forEach(offer -> {
            OfferResponseDTO response = toOfferResponse(offer);
            publishAfterCommit(() -> notificationClient.sendOfferExpiredNotification(response));
        });
        log.info("Expired {} pending product offers", saved.size());
    }

    /**
     * Auto-approves return requests where the seller has not responded within the timeout period.
     * After seller auto-approval, admin must still approve for the return to be finalized.
     */
    @Scheduled(fixedDelayString = "${orders.return-timeout-scan-ms:3600000}")
    public void autoApproveTimedOutReturnRequests() {
        if (returnApprovalTimeoutHours <= 0) {
            return;
        }
        LocalDateTime timeoutBefore = LocalDateTime.now().minusHours(returnApprovalTimeoutHours);
        List<Order> timedOutReturns = orderRepository.findReturnRequestsPendingSellerApproval(timeoutBefore);
        if (timedOutReturns.isEmpty()) {
            return;
        }

        for (Order order : timedOutReturns) {
            order.setReturnSellerApproved(true);
            Order saved = orderRepository.save(order);
            log.warn("Auto-approved return request for order {} after seller did not respond within  hours",
                    saved.getId(), returnApprovalTimeoutHours);
            // Check if both seller and admin have now approved
            if (saved.isReturnAdminApproved()) {
                // Finalize the return
                saved.setStatus(OrderStatus.RETURNED);
                orderRepository.save(saved);
                publishAfterCommit(() -> paymentClient.refundDeposit(saved.getId()));
                log.info("Return finalized for order {} after auto-approval", saved.getId());
            }
        }

        log.info("Auto-approved {} timed-out return requests", timedOutReturns.size());
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private ProductOffer findOffer(Long offerId) {
        return productOfferRepository.findById(offerId)
                .orElseThrow(() -> new OrderStateException("Offer was not found"));
    }

    private ProductOffer expireOfferIfNeeded(ProductOffer offer) {
        if (offer.getStatus() == OfferStatus.PENDING
                && offer.getExpiresAt() != null
                && offer.getExpiresAt().isBefore(LocalDateTime.now())) {
            try {
                offer.setStatus(OfferStatus.EXPIRED);
                return productOfferRepository.save(offer);
            } catch (OptimisticLockingFailureException ex) {
                // Another thread already expired or modified this offer; refetch to get latest state
                return productOfferRepository.findById(offer.getId())
                        .orElseThrow(() -> new OrderStateException("Offer was not found"));
            }
        }
        return offer;
    }

    private ProductOffer resolveCheckoutOffer(Long offerId, ProductResponseDTO product, String buyerId) {
        if (offerId == null) {
            return null;
        }

        ProductOffer offer = expireOfferIfNeeded(findOffer(offerId));
        if (!offer.getBuyerId().equals(buyerId)) {
            throw new OrderStateException("Offer does not belong to the current buyer");
        }
        if (!offer.getProductId().equals(product.id())) {
            throw new OrderStateException("Offer does not belong to this product");
        }
        if (!offer.getSellerId().equals(product.sellerId())) {
            throw new OrderStateException("Offer seller does not match this product");
        }
        if (offer.getStatus() != OfferStatus.ACCEPTED) {
            throw new OrderStateException("Only accepted offers can be used for checkout");
        }
        return offer;
    }

    private void cancelCompetingOffers(Long productId, ProductOffer checkoutOffer) {
        Long usedOfferId = checkoutOffer != null ? checkoutOffer.getId() : null;
        List<ProductOffer> competingOffers = productOfferRepository.findByProductIdAndStatusIn(productId, ACTIVE_OFFER_STATUSES)
                .stream()
                .filter(offer -> usedOfferId == null || !usedOfferId.equals(offer.getId()))
                .toList();
        if (competingOffers.isEmpty()) {
            return;
        }

        competingOffers.forEach(offer -> offer.setStatus(OfferStatus.CANCELLED));
        List<ProductOffer> savedOffers = productOfferRepository.saveAll(competingOffers);
        savedOffers.forEach(offer -> {
            OfferResponseDTO response = toOfferResponse(offer);
            publishAfterCommit(() -> notificationClient.sendOfferProductUnavailableNotification(response));
        });
        log.info("Cancelled {} competing offers for product {}", savedOffers.size(), productId);
    }

    private BigDecimal resolveProductAmount(Order order, BigDecimal fallbackPrice) {
        if (order.getProductAmount() != null && order.getProductAmount().compareTo(BigDecimal.ZERO) > 0) {
            return order.getProductAmount();
        }
        if (order.getAmount() != null && order.getShippingFee() != null) {
            return order.getAmount().subtract(order.getShippingFee());
        }
        return fallbackPrice;
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

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<OrderResponseDTO> listAllOrdersForAdmin(String status, Pageable pageable) {
        OrderStatus orderStatus = (status != null && !status.isBlank()) ? OrderStatus.valueOf(status.toUpperCase()) : null;
        Page<OrderResponseDTO> page = orderRepository.findAllForAdmin(orderStatus, pageable).map(this::toResponse);
        return PageResponseDTO.from(page);
    }

    @Override
    public OrderResponseDTO adminCancelOrder(Long orderId) {
        Order order = findOrder(orderId);
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderStateException("Only pending or processing orders can be admin-cancelled");
        }
        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);
        OrderResponseDTO response = toResponse(savedOrder);
        publishAfterCommit(() -> notificationClient.sendOrderCancelledNotification(response));
        publishAfterCommit(() -> paymentClient.refundDeposit(savedOrder.getId()));
        log.info("Admin cancelled order {}", savedOrder.getId());
        return response;
    }

    @Override
    public OrderResponseDTO requestReturn(Long orderId, String buyerId, String reason, List<MultipartFile> returnEvidenceImages) {
        Order order = findOrder(orderId);
        if (!order.getBuyerId().equals(buyerId)) {
            throw new OrderStateException("Only the buyer can request a return for this order");
        }
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new OrderStateException("Only completed orders can be returned");
        }
        LocalDateTime completedAt = order.getCompletedAt() != null ? order.getCompletedAt() : order.getUpdatedAt();
        if (returnWindowDays > 0 && completedAt != null
                && completedAt.isBefore(LocalDateTime.now().minusDays(returnWindowDays))) {
            throw new OrderStateException("The " + returnWindowDays + "-day return window has expired");
        }

        // Return evidence images are required and validated
        List<MultipartFile> normalizedReturnImages = normalizeImages(returnEvidenceImages);
        fileValidator.validateImages(normalizedReturnImages); // Add validation
        List<String> returnEvidenceUrls = saveUploadedFiles(normalizedReturnImages);

        order.setStatus(OrderStatus.RETURN_REQUESTED);
        order.setReturnReason(reason);
        order.setReturnRequestedAt(LocalDateTime.now());
        order.setReturnSellerApproved(false);
        order.setReturnAdminApproved(false);
        order.setReturnRejectReason(null);
        order.setReturnEvidenceImages(writeJson(returnEvidenceUrls));
        Order saved = orderRepository.save(order);
        log.info("Return requested for order {} by buyer {} with {} evidence images", saved.getId(), buyerId, returnEvidenceUrls.size());
        return toResponse(saved);
    }

    @Override
    public OrderResponseDTO sellerApproveReturn(Long orderId, String sellerId) {
        Order order = requireReturnRequested(orderId);
        if (!order.getSellerId().equals(sellerId)) {
            throw new OrderStateException("Only the seller can approve this return");
        }
        order.setReturnSellerApproved(true);
        return finalizeOrAwaitReturn(order, "seller");
    }

    @Override
    public OrderResponseDTO adminApproveReturn(Long orderId) {
        Order order = requireReturnRequested(orderId);
        order.setReturnAdminApproved(true);
        return finalizeOrAwaitReturn(order, "admin");
    }

    @Override
    public OrderResponseDTO sellerRejectReturn(Long orderId, String sellerId, String reason) {
        Order order = requireReturnRequested(orderId);
        if (!order.getSellerId().equals(sellerId)) {
            throw new OrderStateException("Only the seller can reject this return");
        }
        return doRejectReturn(order, reason, "seller");
    }

    @Override
    public OrderResponseDTO adminRejectReturn(Long orderId, String reason) {
        Order order = requireReturnRequested(orderId);
        return doRejectReturn(order, reason, "admin");
    }

    private Order requireReturnRequested(Long orderId) {
        Order order = findOrder(orderId);
        if (order.getStatus() != OrderStatus.RETURN_REQUESTED) {
            throw new OrderStateException("Order is not awaiting return approval");
        }
        return order;
    }

    /** Finalizes the return only when BOTH seller and admin have approved; otherwise waits for the other party. */
    private OrderResponseDTO finalizeOrAwaitReturn(Order order, String approver) {
        if (order.isReturnSellerApproved() && order.isReturnAdminApproved()) {
            order.setStatus(OrderStatus.RETURNED);
            Order saved = orderRepository.save(order);
            // Deposit refunded (marked); product stays SOLD — seller must re-list manually.
            publishAfterCommit(() -> paymentClient.refundDeposit(saved.getId()));
            log.info("Return finalized for order {} after {} approval (both parties approved)", saved.getId(), approver);
            return toResponse(saved);
        }
        Order saved = orderRepository.save(order);
        log.info("Return for order {} approved by {}; awaiting the other party", saved.getId(), approver);
        return toResponse(saved);
    }

    private OrderResponseDTO doRejectReturn(Order order, String reason, String rejecter) {
        order.setStatus(OrderStatus.COMPLETED);
        order.setReturnRejectReason(reason);
        order.setReturnSellerApproved(false);
        order.setReturnAdminApproved(false);
        Order saved = orderRepository.save(order);
        log.info("Return for order {} rejected by {}", saved.getId(), rejecter);
        return toResponse(saved);
    }

    private List<MultipartFile> normalizeImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            throw new OrderStateException("At least one evidence image is required");
        }
        List<MultipartFile> normalized = images.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        if (normalized.isEmpty()) {
            throw new OrderStateException("At least one evidence image is required");
        }
        return normalized;
    }

    private List<String> saveUploadedFiles(List<MultipartFile> files) {
        List<String> relativeUrls = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            relativeUrls.add(saveUploadedFile(file));
        }
        return relativeUrls;
    }

    private String saveUploadedFile(MultipartFile file) {
        String originalFilename = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename().trim()
                : "evidence.jpg";
        String extension = "";
        int lastDotIndex = originalFilename.lastIndexOf('.');
        if (lastDotIndex >= 0) {
            extension = originalFilename.substring(lastDotIndex);
        }
        String storedFilename = UUID.randomUUID() + extension;
        Path uploadDir = Paths.get(storageProperties.uploadDir()).toAbsolutePath().normalize();
        Path targetPath = uploadDir.resolve(storedFilename);
        try {
            Files.createDirectories(uploadDir);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ShippingProviderUnavailableException("Unable to store evidence image", exception);
        }
        return "api/v1/orders/media/" + storedFilename;
    }

    private String writeJson(List<String> urls) {
        try {
            return objectMapper.writeValueAsString(urls);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException exception) {
            return "[]";
        }
    }

    private List<String> toAbsoluteEvidenceUrls(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        List<String> relative;
        try {
            relative = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (IOException exception) {
            return Collections.emptyList();
        }
        String base = applicationProperties.publicBaseUrl();
        return relative.stream().map(url -> toAbsoluteUrl(base, url)).toList();
    }

    private String toAbsoluteUrl(String base, String url) {
        if (!StringUtils.hasText(url) || url.startsWith("http://") || url.startsWith("https://") || !StringUtils.hasText(base)) {
            return url;
        }
        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String normalizedPath = url.startsWith("/") ? url : "/" + url;
        return normalizedBase + normalizedPath;
    }

    private OrderResponseDTO toResponse(Order order) {
        return OrderResponseDTO.builder()
                .id(order.getId())
                .buyerId(order.getBuyerId())
                .sellerId(order.getSellerId())
                .productId(order.getProductId())
                .productTitle(order.getProductTitle())
                .productImageUrl(order.getProductImageUrl())
                .amount(order.getAmount())
                .productAmount(resolveProductAmount(order, BigDecimal.ZERO))
                .shippingFee(order.getShippingFee())
                .platformFee(order.getPlatformFee())
                .sellerShippingFeePaid(order.isSellerShippingFeePaid())
                .trackingCode(order.getTrackingCode())
                .deliveryMethod(order.getDeliveryMethod())
                .status(order.getStatus())
                .completedAt(order.getCompletedAt())
                .returnReason(order.getReturnReason())
                .returnRequestedAt(order.getReturnRequestedAt())
                .returnSellerApproved(order.isReturnSellerApproved())
                .returnAdminApproved(order.isReturnAdminApproved())
                .returnRejectReason(order.getReturnRejectReason())
                .evidenceImages(toAbsoluteEvidenceUrls(order.getEvidenceImages()))
                .returnEvidenceImages(toAbsoluteEvidenceUrls(order.getReturnEvidenceImages()))
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private OfferResponseDTO toOfferResponse(ProductOffer offer) {
        return OfferResponseDTO.builder()
                .id(offer.getId())
                .productId(offer.getProductId())
                .buyerId(offer.getBuyerId())
                .sellerId(offer.getSellerId())
                .originalPrice(offer.getOriginalPrice())
                .offerPrice(offer.getOfferPrice())
                .discountPercent(offer.getDiscountPercent())
                .status(offer.getStatus())
                .expiresAt(offer.getExpiresAt())
                .createdAt(offer.getCreatedAt())
                .updatedAt(offer.getUpdatedAt())
                .build();
    }
}
