package com.hsmart.order.presentation.controllers;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hsmart.order.application.dto.GhtkWebhookRequestDTO;
import com.hsmart.order.application.dto.OrderResponseDTO;
import com.hsmart.order.application.exceptions.ShippingProviderUnavailableException;
import com.hsmart.order.domain.entities.DeliveryMethod;
import com.hsmart.order.domain.entities.OrderStatus;
import com.hsmart.order.infrastructure.exception.ApiExceptionHandler;
import com.hsmart.order.service.OrderService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OrderControllerTest {

    private OrderService orderService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createOrderShouldRequireGatewayUserContext() throws Exception {
        mockMvc.perform(post("/api/v1/orders/internal/from-deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 10,
                                  "deliveryMethod": "GHTK"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Missing authenticated user context"));
    }

    @Test
    void createOrderShouldReturnCreatedWithSelectedDeliveryMethod() throws Exception {
        given(orderService.createOrder(any(), eq("buyer-one"))).willReturn(OrderResponseDTO.builder()
                .id(5L)
                .buyerId("buyer-one")
                .sellerId("seller-one")
                .productId(10L)
                .amount(BigDecimal.valueOf(127000))
                .shippingFee(BigDecimal.valueOf(27000))
                .deliveryMethod(DeliveryMethod.VIETTEL_POST)
                .status(OrderStatus.PENDING)
                .build());

        mockMvc.perform(post("/api/v1/orders/internal/from-deposit")
                        .header("X-User-Id", "buyer-one")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 10,
                                  "deliveryMethod": "VIETTEL_POST"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("Order created successfully"))
                .andExpect(jsonPath("$.data.deliveryMethod").value("VIETTEL_POST"))
                .andExpect(jsonPath("$.data.shippingFee").value(27000));
    }

    @Test
    void createOrderShouldReturnServiceUnavailableWhenShippingProviderFails() throws Exception {
        given(orderService.createOrder(any(), eq("buyer-one")))
                .willThrow(new ShippingProviderUnavailableException("Shipping fee calculation failed"));

        mockMvc.perform(post("/api/v1/orders/internal/from-deposit")
                        .header("X-User-Id", "buyer-one")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 10,
                                  "deliveryMethod": "GHTK"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Shipping provider is temporarily unavailable"));
    }

    @Test
    void createOrderShouldRejectUnsupportedDeliveryMethodPayload() throws Exception {
        mockMvc.perform(post("/api/v1/orders/internal/from-deposit")
                        .header("X-User-Id", "buyer-one")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 10,
                                  "deliveryMethod": "SELF_ARRANGED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void ghtkWebhookShouldAcceptFormPayloadAndReturnStandardApiResponse() throws Exception {
        mockMvc.perform(post("/api/v1/orders/internal/ghtk-webhook")
                        .queryParam("hash", "webhook-secret")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("label_id", "S1.A1.12345")
                        .param("partner_id", "5")
                        .param("status_id", "5")
                        .param("action_time", "2026-06-01T10:00:00+07:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("GHTK webhook processed successfully"))
                .andExpect(jsonPath("$.data").doesNotExist());

        ArgumentCaptor<GhtkWebhookRequestDTO> requestCaptor = ArgumentCaptor.forClass(GhtkWebhookRequestDTO.class);
        verify(orderService).processGhtkWebhook(eq("webhook-secret"), requestCaptor.capture());
        GhtkWebhookRequestDTO request = requestCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(request.labelId()).isEqualTo("S1.A1.12345");
        org.assertj.core.api.Assertions.assertThat(request.statusId()).isEqualTo(5);
    }
}
