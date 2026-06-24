package com.hsmart.order.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.hsmart.order.application.dto.CreateOrderRequestDTO;
import com.hsmart.order.application.dto.CreateOfferRequestDTO;
import com.hsmart.order.application.dto.GhtkShipmentRequestDTO;
import com.hsmart.order.application.dto.GhtkWebhookRequestDTO;
import com.hsmart.order.application.dto.OfferResponseDTO;
import com.hsmart.order.application.dto.OrderResponseDTO;
import com.hsmart.order.application.dto.PageResponseDTO;
import com.hsmart.order.application.dto.ProductResponseDTO;
import com.hsmart.order.application.dto.ShippingEstimateResponseDTO;
import com.hsmart.order.application.dto.UserAddressResponseDTO;
import com.hsmart.order.application.exceptions.InvalidGhtkWebhookException;
import com.hsmart.order.application.exceptions.ShippingProviderUnavailableException;
import com.hsmart.order.domain.entities.DeliveryMethod;
import com.hsmart.order.domain.entities.OfferStatus;
import com.hsmart.order.domain.entities.Order;
import com.hsmart.order.domain.entities.OrderStatus;
import com.hsmart.order.domain.entities.ProductOffer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.order.infrastructure.config.ApplicationProperties;
import com.hsmart.order.infrastructure.config.GhtkProperties;
import com.hsmart.order.infrastructure.config.StorageProperties;
import com.hsmart.order.infrastructure.messaging.OrderEventPublisher;
import com.hsmart.order.infrastructure.persistence.OrderRepository;
import com.hsmart.order.infrastructure.persistence.ProductOfferRepository;
import com.hsmart.order.service.GhtkClient;
import com.hsmart.order.service.NotificationClient;
import com.hsmart.order.service.ProductClient;
import com.hsmart.order.service.ViettelPostClient;
import com.hsmart.order.service.UserClient;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductOfferRepository productOfferRepository;

    @Mock
    private ProductClient productClient;

    @Mock
    private UserClient userClient;

    @Mock
    private GhtkClient ghtkClient;

    @Mock
    private ViettelPostClient viettelPostClient;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private com.hsmart.order.service.PaymentClient paymentClient;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
                orderRepository,
                productOfferRepository,
                productClient,
                userClient,
                java.util.List.of(ghtkClient, viettelPostClient),
                orderEventPublisher,
                ghtkProperties(),
                notificationClient,
                paymentClient,
                new StorageProperties(System.getProperty("java.io.tmpdir") + "/hsmart-order-test-uploads"),
                new ApplicationProperties("http://localhost:8000"),
                new ObjectMapper()
        );
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(orderRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(productOfferRepository.save(any(ProductOffer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(productOfferRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(ghtkClient.deliveryMethod()).thenReturn(DeliveryMethod.GHTK);
        lenient().when(viettelPostClient.deliveryMethod()).thenReturn(DeliveryMethod.VIETTEL_POST);
    }

    @Test
    void confirmOrderShouldCreateGhtkShipmentAndMoveOrderToProcessing() {
        Order order = pendingOrder();
        UserAddressResponseDTO sellerAddress = address("seller-one", "District 1");
        UserAddressResponseDTO buyerAddress = address("buyer-one", "Thu Duc City");
        given(orderRepository.findById(5L)).willReturn(java.util.Optional.of(order));
        given(productClient.getProduct(10L, "seller-one")).willReturn(product());
        given(userClient.getUserAddress("seller-one")).willReturn(sellerAddress);
        given(userClient.getUserAddress("buyer-one")).willReturn(buyerAddress);
        given(ghtkClient.createShipment(any(GhtkShipmentRequestDTO.class))).willReturn("S1.A1.12345");

        OrderResponseDTO response = orderService.confirmOrder(5L, "seller-one", evidenceImages());

        ArgumentCaptor<GhtkShipmentRequestDTO> requestCaptor = ArgumentCaptor.forClass(GhtkShipmentRequestDTO.class);
        verify(ghtkClient).createShipment(requestCaptor.capture());
        // COD now collects product amount only (shipping was prepaid as the deposit)
        assertThat(requestCaptor.getValue().codAmount()).isEqualByComparingTo("100000");
        assertThat(requestCaptor.getValue().productValue()).isEqualByComparingTo("100000");
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(response.getTrackingCode()).isEqualTo("S1.A1.12345");
    }

    @Test
    void confirmOrderShouldUseViettelPostShipmentWhenOrderWasCreatedWithViettelPost() {
        Order order = pendingOrder();
        order.setDeliveryMethod(DeliveryMethod.VIETTEL_POST);
        UserAddressResponseDTO sellerAddress = address("seller-one", "District 1");
        UserAddressResponseDTO buyerAddress = address("buyer-one", "Thu Duc City");
        given(orderRepository.findById(5L)).willReturn(java.util.Optional.of(order));
        given(productClient.getProduct(10L, "seller-one")).willReturn(product());
        given(userClient.getUserAddress("seller-one")).willReturn(sellerAddress);
        given(userClient.getUserAddress("buyer-one")).willReturn(buyerAddress);
        given(viettelPostClient.createShipment(any(GhtkShipmentRequestDTO.class))).willReturn("VTP123456");

        OrderResponseDTO response = orderService.confirmOrder(5L, "seller-one", evidenceImages());

        verify(viettelPostClient).createShipment(any(GhtkShipmentRequestDTO.class));
        verify(ghtkClient, never()).createShipment(any(GhtkShipmentRequestDTO.class));
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(response.getTrackingCode()).isEqualTo("VTP123456");
    }

    @Test
    void confirmOrderShouldRejectCallerWhoIsNotSeller() {
        given(orderRepository.findById(5L)).willReturn(java.util.Optional.of(pendingOrder()));

        assertThatThrownBy(() -> orderService.confirmOrder(5L, "another-seller", evidenceImages()))
                .isInstanceOf(com.hsmart.order.application.exceptions.OrderStateException.class)
                .hasMessage("Only the seller can confirm this order");
    }

    @Test
    void processGhtkWebhookShouldCompleteProcessingOrderAndPublishEvent() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.PROCESSING);
        order.setTrackingCode("S1.A1.12345");
        given(orderRepository.findByTrackingCode("S1.A1.12345")).willReturn(java.util.Optional.of(order));

        orderService.processGhtkWebhook("webhook-secret", webhook(5));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(orderEventPublisher).publishOrderCompleted(any());
    }

    @Test
    void processGhtkWebhookShouldIgnoreDuplicateCompletedDelivery() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.COMPLETED);
        order.setTrackingCode("S1.A1.12345");
        given(orderRepository.findByTrackingCode("S1.A1.12345")).willReturn(java.util.Optional.of(order));

        orderService.processGhtkWebhook("webhook-secret", webhook(5));

        verify(orderEventPublisher, never()).publishOrderCompleted(any());
    }

    @Test
    void processGhtkWebhookShouldRejectInvalidHash() {
        assertThatThrownBy(() -> orderService.processGhtkWebhook("wrong-secret", webhook(5)))
                .isInstanceOf(InvalidGhtkWebhookException.class)
                .hasMessage("Invalid GHTK webhook credentials");
    }

    @Test
    void createOrderShouldAddShippingFeeToProductPrice() {
        UserAddressResponseDTO sellerAddress = address("seller-one", "District 1");
        UserAddressResponseDTO buyerAddress = address("buyer-one", "Thu Duc City");
        given(productClient.getProduct(10L, "buyer-one")).willReturn(product());
        given(userClient.getUserAddress("seller-one")).willReturn(sellerAddress);
        given(userClient.getUserAddress("buyer-one")).willReturn(buyerAddress);
        given(ghtkClient.calculateShippingFee(sellerAddress, buyerAddress)).willReturn(BigDecimal.valueOf(30000));

        OrderResponseDTO response = orderService.createOrder(ghtkOrderRequest(10L), "buyer-one");

        assertThat(response.getShippingFee()).isEqualByComparingTo("30000");
        assertThat(response.getAmount()).isEqualByComparingTo("130000");
        verify(ghtkClient).calculateShippingFee(sellerAddress, buyerAddress);
    }

    @Test
    void createOrderShouldRejectOrderWhenGhtkFeeCalculationFails() {
        UserAddressResponseDTO sellerAddress = address("seller-one", "District 1");
        UserAddressResponseDTO buyerAddress = address("buyer-one", "Thu Duc City");
        given(productClient.getProduct(10L, "buyer-one")).willReturn(product());
        given(userClient.getUserAddress("seller-one")).willReturn(sellerAddress);
        given(userClient.getUserAddress("buyer-one")).willReturn(buyerAddress);
        given(ghtkClient.calculateShippingFee(sellerAddress, buyerAddress))
                .willThrow(new IllegalStateException("GHTK is unavailable"));

        assertThatThrownBy(() -> orderService.createOrder(ghtkOrderRequest(10L), "buyer-one"))
                .isInstanceOf(ShippingProviderUnavailableException.class)
                .hasMessage("Shipping fee calculation failed");
    }

    @Test
    void createOrderShouldUseViettelPostShippingFeeWhenSelected() {
        UserAddressResponseDTO sellerAddress = address("seller-one", "District 1");
        UserAddressResponseDTO buyerAddress = address("buyer-one", "Thu Duc City");
        given(productClient.getProduct(10L, "buyer-one")).willReturn(product());
        given(userClient.getUserAddress("seller-one")).willReturn(sellerAddress);
        given(userClient.getUserAddress("buyer-one")).willReturn(buyerAddress);
        given(viettelPostClient.calculateShippingFee(sellerAddress, buyerAddress)).willReturn(BigDecimal.valueOf(27000));
        CreateOrderRequestDTO request = CreateOrderRequestDTO.builder()
                .productId(10L)
                .deliveryMethod(DeliveryMethod.VIETTEL_POST)
                .build();

        OrderResponseDTO response = orderService.createOrder(request, "buyer-one");

        assertThat(response.getShippingFee()).isEqualByComparingTo("27000");
        assertThat(response.getAmount()).isEqualByComparingTo("127000");
        assertThat(response.getDeliveryMethod()).isEqualTo(DeliveryMethod.VIETTEL_POST);
        verify(ghtkClient, never()).calculateShippingFee(any(), any());
        verify(viettelPostClient).calculateShippingFee(sellerAddress, buyerAddress);
    }

    @Test
    void estimateShippingShouldReturnProviderFeeAndSellerLocation() {
        UserAddressResponseDTO sellerAddress = address("seller-one", "District 1");
        UserAddressResponseDTO buyerAddress = address("buyer-one", "Thu Duc City");
        given(productClient.getProduct(10L, "buyer-one")).willReturn(product());
        given(userClient.getUserAddress("seller-one")).willReturn(sellerAddress);
        given(userClient.getUserAddress("buyer-one")).willReturn(buyerAddress);
        given(ghtkClient.calculateShippingFee(sellerAddress, buyerAddress)).willReturn(BigDecimal.valueOf(30000));

        ShippingEstimateResponseDTO response = orderService.estimateShipping(10L, DeliveryMethod.GHTK, "buyer-one");

        assertThat(response.getShippingFee()).isEqualByComparingTo("30000");
        assertThat(response.getEstimatedTotal()).isEqualByComparingTo("130000");
        assertThat(response.getSellerDistrict()).isEqualTo("District 1");
    }

    @Test
    void createOfferShouldCalculateDiscountedOfferPrice() {
        given(productClient.getProduct(10L, "buyer-one")).willReturn(product());
        when(productOfferRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OfferResponseDTO response = orderService.createOffer(
                CreateOfferRequestDTO.builder()
                        .productId(10L)
                        .discountPercent(10)
                        .build(),
                "buyer-one"
        );

        assertThat(response.getOfferPrice()).isEqualByComparingTo("90000");
        assertThat(response.getDiscountPercent()).isEqualTo(10);
        assertThat(response.getSellerId()).isEqualTo("seller-one");
    }

    @Test
    void createOfferShouldRejectDuplicateActiveOfferFromSameBuyer() {
        given(productClient.getProduct(10L, "buyer-one")).willReturn(product());
        given(productOfferRepository.existsByProductIdAndBuyerIdAndStatusIn(eq(10L), eq("buyer-one"), any()))
                .willReturn(true);

        assertThatThrownBy(() -> orderService.createOffer(
                CreateOfferRequestDTO.builder()
                        .productId(10L)
                        .discountPercent(10)
                        .build(),
                "buyer-one"
        ))
                .isInstanceOf(com.hsmart.order.application.exceptions.OrderStateException.class)
                .hasMessage("Buyer already has an active offer for this product");
    }

    @Test
    void createOfferShouldRejectWhenProductNotNegotiable() {
        given(productClient.getProduct(10L, "buyer-one"))
                .willReturn(negotiableProduct(false, null));

        assertThatThrownBy(() -> orderService.createOffer(
                CreateOfferRequestDTO.builder()
                        .productId(10L)
                        .discountPercent(10)
                        .build(),
                "buyer-one"
        ))
                .isInstanceOf(com.hsmart.order.application.exceptions.OrderStateException.class)
                .hasMessage("This product is not open to offers");
    }

    @Test
    void createOfferShouldRejectOfferBelowSellerMinimum() {
        given(productClient.getProduct(10L, "buyer-one"))
                .willReturn(negotiableProduct(true, BigDecimal.valueOf(95000)));

        assertThatThrownBy(() -> orderService.createOffer(
                CreateOfferRequestDTO.builder()
                        .productId(10L)
                        .discountPercent(10)
                        .build(),
                "buyer-one"
        ))
                .isInstanceOf(com.hsmart.order.application.exceptions.OrderStateException.class)
                .hasMessage("Offer price is below the seller's minimum acceptable price");
    }

    @Test
    void createOfferShouldAllowDiscountAboveThirtyPercentWhenAboveMinimum() {
        given(productClient.getProduct(10L, "buyer-one"))
                .willReturn(negotiableProduct(true, BigDecimal.valueOf(50000)));
        when(productOfferRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OfferResponseDTO response = orderService.createOffer(
                CreateOfferRequestDTO.builder()
                        .productId(10L)
                        .discountPercent(40)
                        .build(),
                "buyer-one"
        );

        assertThat(response.getOfferPrice()).isEqualByComparingTo("60000");
        assertThat(response.getDiscountPercent()).isEqualTo(40);
    }

    @Test
    void acceptOfferShouldMovePendingOfferToAccepted() {
        ProductOffer offer = productOffer(OfferStatus.PENDING);
        given(productOfferRepository.findById(77L)).willReturn(java.util.Optional.of(offer));

        OfferResponseDTO response = orderService.acceptOffer(77L, "seller-one");

        assertThat(response.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
        verify(notificationClient).sendOfferAcceptedNotification(any());
    }

    @Test
    void createOrderShouldUseAcceptedOfferPriceAndMarkOfferAsOrdered() {
        UserAddressResponseDTO sellerAddress = address("seller-one", "District 1");
        UserAddressResponseDTO buyerAddress = address("buyer-one", "Thu Duc City");
        ProductOffer offer = productOffer(OfferStatus.ACCEPTED);
        given(productClient.getProduct(10L, "buyer-one")).willReturn(product());
        given(userClient.getUserAddress("seller-one")).willReturn(sellerAddress);
        given(userClient.getUserAddress("buyer-one")).willReturn(buyerAddress);
        given(ghtkClient.calculateShippingFee(sellerAddress, buyerAddress)).willReturn(BigDecimal.valueOf(30000));
        given(productOfferRepository.findById(77L)).willReturn(java.util.Optional.of(offer));
        given(productOfferRepository.findByProductIdAndStatusIn(eq(10L), any())).willReturn(java.util.List.of());
        CreateOrderRequestDTO request = CreateOrderRequestDTO.builder()
                .productId(10L)
                .deliveryMethod(DeliveryMethod.GHTK)
                .offerId(77L)
                .build();

        OrderResponseDTO response = orderService.createOrder(request, "buyer-one");

        assertThat(response.getProductAmount()).isEqualByComparingTo("85000");
        assertThat(response.getAmount()).isEqualByComparingTo("115000");
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.ORDERED);
    }

    @Test
    void createOrderShouldAllowProductWithActiveStatus() {
        UserAddressResponseDTO sellerAddress = address("seller-one", "District 1");
        UserAddressResponseDTO buyerAddress = address("buyer-one", "Thu Duc City");
        given(productClient.getProduct(10L, "buyer-one")).willReturn(activeProduct());
        given(userClient.getUserAddress("seller-one")).willReturn(sellerAddress);
        given(userClient.getUserAddress("buyer-one")).willReturn(buyerAddress);
        given(ghtkClient.calculateShippingFee(sellerAddress, buyerAddress)).willReturn(BigDecimal.valueOf(30000));

        OrderResponseDTO response = orderService.createOrder(ghtkOrderRequest(10L), "buyer-one");

        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void createOrderShouldRejectProductWithPendingReviewStatus() {
        given(productClient.getProduct(10L, "buyer-one")).willReturn(pendingReviewProduct());

        assertThatThrownBy(() -> orderService.createOrder(ghtkOrderRequest(10L), "buyer-one"))
                .isInstanceOf(com.hsmart.order.application.exceptions.ProductUnavailableException.class)
                .hasMessage("Product is not available for ordering");
    }

    @Test
    void createOrderShouldRejectBuyerOrderingOwnProduct() {
        given(productClient.getProduct(10L, "seller-one")).willReturn(product());

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequestDTO(10L), "seller-one"))
                .isInstanceOf(com.hsmart.order.application.exceptions.ProductUnavailableException.class)
                .hasMessage("Buyers cannot order their own products");
    }

    @Test
    void createOrderShouldRejectProductWithMissingSellerInformation() {
        given(productClient.getProduct(10L, "buyer-one")).willReturn(productWithSeller(null));

        assertThatThrownBy(() -> orderService.createOrder(ghtkOrderRequest(10L), "buyer-one"))
                .isInstanceOf(com.hsmart.order.application.exceptions.ProductUnavailableException.class)
                .hasMessage("Product seller information is missing");
    }

    @Test
    void createOrderShouldRejectProductWithMissingPrice() {
        given(productClient.getProduct(10L, "buyer-one")).willReturn(productWithPrice(null));

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequestDTO(10L), "buyer-one"))
                .isInstanceOf(com.hsmart.order.application.exceptions.ProductUnavailableException.class)
                .hasMessage("Product price is missing");
    }

    @Test
    void createOrderShouldRejectProductWithActiveOrder() {
        given(productClient.getProduct(10L, "buyer-one")).willReturn(product());
        given(orderRepository.existsByProductIdAndStatusIn(eq(10L), any())).willReturn(true);

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequestDTO(10L), "buyer-one"))
                .isInstanceOf(com.hsmart.order.application.exceptions.ProductUnavailableException.class)
                .hasMessage("Product already has an active order");
    }

    @Test
    void createOrderShouldRejectDatabaseLevelDuplicateActiveOrder() {
        UserAddressResponseDTO sellerAddress = address("seller-one", "District 1");
        UserAddressResponseDTO buyerAddress = address("buyer-one", "Thu Duc City");
        given(productClient.getProduct(10L, "buyer-one")).willReturn(product());
        given(userClient.getUserAddress("seller-one")).willReturn(sellerAddress);
        given(userClient.getUserAddress("buyer-one")).willReturn(buyerAddress);
        given(ghtkClient.calculateShippingFee(sellerAddress, buyerAddress)).willReturn(BigDecimal.valueOf(30000));
        given(orderRepository.save(any(Order.class))).willThrow(new DataIntegrityViolationException("duplicate active order"));

        assertThatThrownBy(() -> orderService.createOrder(ghtkOrderRequest(10L), "buyer-one"))
                .isInstanceOf(com.hsmart.order.application.exceptions.ProductUnavailableException.class)
                .hasMessage("Product already has an active order");
    }

    @Test
    void createOrderShouldRejectSelectedProviderWhenClientIsNotConfigured() {
        orderService = new OrderServiceImpl(
                orderRepository,
                productOfferRepository,
                productClient,
                userClient,
                java.util.List.of(ghtkClient),
                orderEventPublisher,
                ghtkProperties(),
                notificationClient,
                paymentClient,
                new StorageProperties(System.getProperty("java.io.tmpdir") + "/hsmart-order-test-uploads"),
                new ApplicationProperties("http://localhost:8000"),
                new ObjectMapper()
        );
        given(productClient.getProduct(10L, "buyer-one")).willReturn(product());
        CreateOrderRequestDTO request = CreateOrderRequestDTO.builder()
                .productId(10L)
                .deliveryMethod(DeliveryMethod.VIETTEL_POST)
                .build();

        assertThatThrownBy(() -> orderService.createOrder(request, "buyer-one"))
                .isInstanceOf(com.hsmart.order.application.exceptions.OrderStateException.class)
                .hasMessage("Unsupported delivery method");
    }

    @Test
    void getOrdersForCurrentUserShouldReturnBuyerAndSellerOrders() {
        Order sellingOrder = pendingOrder();
        sellingOrder.setId(6L);
        sellingOrder.setBuyerId("hsmart_buyer");
        sellingOrder.setSellerId("demo_seller_001");
        Order buyingOrder = pendingOrder();
        buyingOrder.setId(7L);
        buyingOrder.setBuyerId("demo_seller_001");
        buyingOrder.setSellerId("another-seller");
        given(orderRepository.findByBuyerIdOrSellerIdOrderByCreatedAtDescIdDesc(
                eq("demo_seller_001"),
                eq("demo_seller_001"),
                any(Pageable.class)
        )).willReturn(new PageImpl<>(java.util.List.of(sellingOrder, buyingOrder)));

        PageResponseDTO<OrderResponseDTO> response = orderService.getOrdersForCurrentUser("demo_seller_001", Pageable.unpaged());

        assertThat(response.getContent()).extracting(OrderResponseDTO::getId).containsExactly(6L, 7L);
        assertThat(response.getContent().get(0).getSellerId()).isEqualTo("demo_seller_001");
        assertThat(response.getContent().get(1).getBuyerId()).isEqualTo("demo_seller_001");
    }

    @Test
    void getOrderShouldOnlyReturnOrdersForBuyerOrSeller() {
        Order order = pendingOrder();
        given(orderRepository.findByIdAndBuyerIdOrIdAndSellerId(5L, "buyer-one", 5L, "buyer-one"))
                .willReturn(java.util.Optional.of(order));

        OrderResponseDTO response = orderService.getOrder(5L, "buyer-one");

        assertThat(response.getId()).isEqualTo(5L);
        assertThat(response.getBuyerId()).isEqualTo("buyer-one");
    }

    @Test
    void completeOrderShouldRejectPendingOrder() {
        given(orderRepository.findById(5L)).willReturn(java.util.Optional.of(pendingOrder()));

        assertThatThrownBy(() -> orderService.completeOrder(5L, "buyer-one"))
                .isInstanceOf(com.hsmart.order.application.exceptions.OrderStateException.class)
                .hasMessage("Only processing orders can be completed");
    }

    @Test
    void cancelOrderShouldAllowBuyerToCancelPendingOrder() {
        Order order = pendingOrder();
        given(orderRepository.findById(5L)).willReturn(java.util.Optional.of(order));

        OrderResponseDTO response = orderService.cancelOrder(5L, "buyer-one");

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelOrderShouldExpireOldPendingOrderBeforeManualCancellation() {
        Order order = pendingOrder();
        order.setCreatedAt(java.time.LocalDateTime.now().minusMinutes(45));
        given(orderRepository.findById(5L)).willReturn(java.util.Optional.of(order));
        ReflectionTestUtils.setField(orderService, "pendingTimeoutMinutes", 30L);

        assertThatThrownBy(() -> orderService.cancelOrder(5L, "buyer-one"))
                .isInstanceOf(com.hsmart.order.application.exceptions.OrderStateException.class)
                .hasMessageContaining("cancel");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
    }

    @Test
    void cancelExpiredPendingOrdersShouldCancelStalePendingRowsAndNotifyParties() {
        Order expired = pendingOrder();
        ReflectionTestUtils.setField(orderService, "pendingTimeoutMinutes", 30L);
        given(orderRepository.findPendingOrdersExpiredBefore(any())).willReturn(java.util.List.of(expired));

        orderService.cancelExpiredPendingOrders();

        assertThat(expired.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).saveAll(any());
        verify(notificationClient).sendOrderCancelledNotification(any());
    }

    @Test
    void requestReturnShouldMoveCompletedOrderToReturnRequested() {
        given(orderRepository.findById(5L)).willReturn(java.util.Optional.of(completedOrder()));

        OrderResponseDTO response = orderService.requestReturn(5L, "buyer-one", "San pham bi loi");

        assertThat(response.getStatus()).isEqualTo(OrderStatus.RETURN_REQUESTED);
        assertThat(response.getReturnReason()).isEqualTo("San pham bi loi");
    }

    @Test
    void requestReturnShouldRejectWhenOrderNotCompleted() {
        given(orderRepository.findById(5L)).willReturn(java.util.Optional.of(pendingOrder()));

        assertThatThrownBy(() -> orderService.requestReturn(5L, "buyer-one", "x"))
                .isInstanceOf(com.hsmart.order.application.exceptions.OrderStateException.class);
    }

    @Test
    void returnIsFinalizedOnlyAfterBothSellerAndAdminApprove() {
        Order order = completedOrder();
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        given(orderRepository.findById(5L)).willReturn(java.util.Optional.of(order));

        OrderResponseDTO afterSeller = orderService.sellerApproveReturn(5L, "seller-one");
        assertThat(afterSeller.getStatus()).isEqualTo(OrderStatus.RETURN_REQUESTED);
        assertThat(afterSeller.isReturnSellerApproved()).isTrue();
        verify(paymentClient, never()).refundDeposit(any());

        OrderResponseDTO afterAdmin = orderService.adminApproveReturn(5L);
        assertThat(afterAdmin.getStatus()).isEqualTo(OrderStatus.RETURNED);
        verify(paymentClient).refundDeposit(5L);
    }

    private Order completedOrder() {
        return Order.builder()
                .id(5L)
                .buyerId("buyer-one")
                .sellerId("seller-one")
                .productId(10L)
                .amount(BigDecimal.valueOf(130000))
                .shippingFee(BigDecimal.valueOf(30000))
                .deliveryMethod(DeliveryMethod.GHTK)
                .status(OrderStatus.COMPLETED)
                .completedAt(java.time.LocalDateTime.now())
                .build();
    }

    private java.util.List<org.springframework.web.multipart.MultipartFile> evidenceImages() {
        return java.util.List.of(new MockMultipartFile("files", "evidence.jpg", "image/jpeg", new byte[]{1, 2, 3}));
    }

    private ProductResponseDTO product() {
        return new ProductResponseDTO(
                10L,
                "Rice cooker",
                "Used rice cooker",
                BigDecimal.valueOf(100000),
                "APPROVED",
                "seller-one",
                1L,
                "Kitchen appliance",
                null,
                true,
                BigDecimal.valueOf(50000)
        );
    }

    private ProductResponseDTO productWithSeller(String sellerId) {
        return new ProductResponseDTO(
                10L,
                "Rice cooker",
                "Used rice cooker",
                BigDecimal.valueOf(100000),
                "APPROVED",
                sellerId,
                1L,
                "Kitchen appliance",
                null,
                true,
                BigDecimal.valueOf(50000)
        );
    }

    private ProductResponseDTO productWithPrice(BigDecimal price) {
        return new ProductResponseDTO(
                10L,
                "Rice cooker",
                "Used rice cooker",
                price,
                "APPROVED",
                "seller-one",
                1L,
                "Kitchen appliance",
                null,
                true,
                BigDecimal.ZERO
        );
    }

    private ProductResponseDTO negotiableProduct(boolean negotiable, BigDecimal minPrice) {
        return new ProductResponseDTO(
                10L,
                "Rice cooker",
                "Used rice cooker",
                BigDecimal.valueOf(100000),
                "APPROVED",
                "seller-one",
                1L,
                "Kitchen appliance",
                null,
                negotiable,
                minPrice
        );
    }

    private ProductResponseDTO activeProduct() {
        return new ProductResponseDTO(
                10L,
                "Rice cooker",
                "Used rice cooker",
                BigDecimal.valueOf(100000),
                "ACTIVE",
                "seller-one",
                1L,
                "Kitchen appliance",
                null,
                true,
                BigDecimal.valueOf(50000)
        );
    }

    private ProductResponseDTO pendingReviewProduct() {
        return new ProductResponseDTO(
                10L,
                "Rice cooker",
                "Used rice cooker",
                BigDecimal.valueOf(100000),
                "PENDING_REVIEW",
                "seller-one",
                1L,
                "Kitchen appliance",
                null,
                true,
                BigDecimal.valueOf(50000)
        );
    }

    private UserAddressResponseDTO address(String userId, String district) {
        return new UserAddressResponseDTO(
                userId,
                userId,
                "0901234567",
                "Ho Chi Minh City",
                district,
                "Ward 1",
                "1 Example Street"
        );
    }

    private Order pendingOrder() {
        return Order.builder()
                .id(5L)
                .buyerId("buyer-one")
                .sellerId("seller-one")
                .productId(10L)
                .amount(BigDecimal.valueOf(130000))
                .shippingFee(BigDecimal.valueOf(30000))
                .deliveryMethod(DeliveryMethod.GHTK)
                .status(OrderStatus.PENDING)
                .build();
    }

    private CreateOrderRequestDTO ghtkOrderRequest(Long productId) {
        return CreateOrderRequestDTO.builder()
                .productId(productId)
                .deliveryMethod(DeliveryMethod.GHTK)
                .build();
    }

    private ProductOffer productOffer(OfferStatus status) {
        return ProductOffer.builder()
                .id(77L)
                .productId(10L)
                .buyerId("buyer-one")
                .sellerId("seller-one")
                .originalPrice(BigDecimal.valueOf(100000))
                .offerPrice(BigDecimal.valueOf(85000))
                .discountPercent(15)
                .status(status)
                .expiresAt(java.time.LocalDateTime.now().plusHours(1))
                .build();
    }

    private GhtkWebhookRequestDTO webhook(int statusId) {
        return new GhtkWebhookRequestDTO("S1.A1.12345", "5", statusId, null, null, null);
    }

    private GhtkProperties ghtkProperties() {
        return new GhtkProperties(
                "https://services.giaohangtietkiem.vn",
                "ghtk-token",
                2000,
                5000,
                1000,
                "HSMART",
                "webhook-secret"
        );
    }
}
