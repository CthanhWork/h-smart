package com.hsmart.order.infrastructure.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.hsmart.order.application.dto.GhtkShipmentRequestDTO;
import com.hsmart.order.application.dto.UserAddressResponseDTO;
import com.hsmart.order.application.exceptions.ShippingProviderUnavailableException;
import com.hsmart.order.infrastructure.config.ViettelPostProperties;
import com.hsmart.order.service.ViettelPostClient;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class ViettelPostShippingClient implements ViettelPostClient {

    private static final String TOKEN_HEADER = "Token";

    private final RestClient viettelPostRestClient;
    private final ViettelPostProperties properties;

    public ViettelPostShippingClient(
            @Qualifier("viettelPostRestClient") RestClient viettelPostRestClient,
            ViettelPostProperties properties
    ) {
        this.viettelPostRestClient = viettelPostRestClient;
        this.properties = properties;
    }

    @Override
    public BigDecimal calculateShippingFee(UserAddressResponseDTO sellerAddress, UserAddressResponseDTO buyerAddress) {
        validateConfiguration();
        validateRequiredAddress(sellerAddress, "seller");
        validateRequiredAddress(buyerAddress, "buyer");

        try {
            JsonNode response = viettelPostRestClient.post()
                    .uri("/v2/order/getPriceNlp")
                    .header(TOKEN_HEADER, getToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toPriceRequest(sellerAddress, buyerAddress, BigDecimal.ZERO))
                    .retrieve()
                    .body(JsonNode.class);
            return readMoney(response, "MONEY_TOTAL", "PRICE", "TOTAL", "MONEY_COLLECTION");
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("Viettel Post shipping fee calculation failed", exception);
            throw new ShippingProviderUnavailableException("Viettel Post shipping fee calculation failed", exception);
        }
    }

    @Override
    public String createShipment(GhtkShipmentRequestDTO request) {
        validateConfiguration();
        validateShipmentRequest(request);

        try {
            JsonNode response = viettelPostRestClient.post()
                    .uri("/v2/order/createOrderNlp")
                    .header(TOKEN_HEADER, getToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toCreateOrderRequest(request))
                    .retrieve()
                    .body(JsonNode.class);

            String trackingCode = readText(response, "ORDER_NUMBER", "ORDER_CODE", "TRACKING_CODE", "ORDER_ID");
            if (StringUtils.hasText(trackingCode)) {
                return trackingCode;
            }
            return request.partnerOrderId();
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("Viettel Post shipment creation failed for partner order {}", request.partnerOrderId(), exception);
            throw new ShippingProviderUnavailableException("Viettel Post shipment creation failed", exception);
        }
    }

    private String getToken() {
        JsonNode response = viettelPostRestClient.post()
                .uri("/v2/user/Login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "USERNAME", properties.username(),
                        "PASSWORD", properties.password()
                ))
                .retrieve()
                .body(JsonNode.class);

        String token = readText(response, "TOKEN", "Token", "token");
        if (!StringUtils.hasText(token)) {
            throw new ShippingProviderUnavailableException("Viettel Post login did not return a token");
        }
        return token;
    }

    private Map<String, Object> toPriceRequest(
            UserAddressResponseDTO sellerAddress,
            UserAddressResponseDTO buyerAddress,
            BigDecimal codAmount
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("PRODUCT_WEIGHT", properties.defaultWeightGrams());
        body.put("PRODUCT_PRICE", codAmount);
        body.put("MONEY_COLLECTION", codAmount);
        body.put("ORDER_SERVICE", properties.serviceCode());
        body.put("ORDER_SERVICE_ADD", properties.serviceExtra());
        body.put("SENDER_ADDRESS", fullAddress(sellerAddress));
        body.put("RECEIVER_ADDRESS", fullAddress(buyerAddress));
        body.put("PRODUCT_LENGTH", 0);
        body.put("PRODUCT_WIDTH", 0);
        body.put("PRODUCT_HEIGHT", 0);
        body.put("PRODUCT_TYPE", properties.productType());
        body.put("NATIONAL_TYPE", 1);
        return body;
    }

    private Map<String, Object> toCreateOrderRequest(GhtkShipmentRequestDTO request) {
        UserAddressResponseDTO seller = request.sellerAddress();
        UserAddressResponseDTO buyer = request.buyerAddress();

        Map<String, Object> productDetail = new LinkedHashMap<>();
        productDetail.put("PRODUCT_NAME", request.productName());
        productDetail.put("PRODUCT_QUANTITY", 1);
        productDetail.put("PRODUCT_PRICE", request.productValue());
        productDetail.put("PRODUCT_WEIGHT", properties.defaultWeightGrams());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ORDER_NUMBER", request.partnerOrderId());
        body.put("SENDER_FULLNAME", contactName(seller));
        body.put("SENDER_ADDRESS", fullAddress(seller));
        body.put("SENDER_PHONE", seller.phoneNumber());
        body.put("RECEIVER_FULLNAME", contactName(buyer));
        body.put("RECEIVER_ADDRESS", fullAddress(buyer));
        body.put("RECEIVER_PHONE", buyer.phoneNumber());
        body.put("PRODUCT_NAME", request.productName());
        body.put("PRODUCT_QUANTITY", 1);
        body.put("PRODUCT_PRICE", request.productValue());
        body.put("PRODUCT_WEIGHT", properties.defaultWeightGrams());
        body.put("PRODUCT_LENGTH", 0);
        body.put("PRODUCT_WIDTH", 0);
        body.put("PRODUCT_HEIGHT", 0);
        body.put("ORDER_PAYMENT", properties.orderPayment());
        body.put("ORDER_SERVICE", properties.serviceCode());
        body.put("PRODUCT_TYPE", properties.productType());
        body.put("ORDER_SERVICE_ADD", properties.serviceExtra());
        body.put("ORDER_NOTE", "Allow the buyer to inspect the product before receiving it");
        body.put("MONEY_COLLECTION", request.codAmount());
        body.put("EXTRA_MONEY", 0);
        body.put("CHECK_UNIQUE", properties.checkUnique());
        body.put("PRODUCT_DETAIL", List.of(productDetail));
        body.put("ENABLE_SORT_CODE", false);
        return body;
    }

    private BigDecimal readMoney(JsonNode response, String... fieldNames) {
        String value = readText(response, fieldNames);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Viettel Post response does not contain a shipping fee");
        }
        return new BigDecimal(value.replace(",", "").trim());
    }

    private String readText(JsonNode response, String... fieldNames) {
        if (response == null || response.isNull()) {
            return null;
        }

        for (String fieldName : fieldNames) {
            JsonNode direct = response.get(fieldName);
            if (direct != null && !direct.isNull()) {
                return direct.asText();
            }
        }

        for (JsonNode child : response) {
            String value = readText(child, fieldNames);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String fullAddress(UserAddressResponseDTO address) {
        return String.join(", ", List.of(
                requireText(address.streetDetail(), "Street detail"),
                requireText(address.ward(), "Ward"),
                requireText(address.district(), "District"),
                requireText(address.province(), "Province")
        ));
    }

    private void validateConfiguration() {
        requireText(properties.apiUrl(), "VIETTEL_POST_API_URL");
        requireText(properties.username(), "VIETTEL_POST_USERNAME");
        requireText(properties.password(), "VIETTEL_POST_PASSWORD");
        requireText(properties.serviceCode(), "VIETTEL_POST_SERVICE_CODE");
        requireText(properties.productType(), "VIETTEL_POST_PRODUCT_TYPE");
    }

    private void validateShipmentRequest(GhtkShipmentRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("Shipment request is required");
        }
        requireText(request.partnerOrderId(), "Partner order id");
        requireText(request.productName(), "Product name");
        if (request.productValue() == null || request.codAmount() == null) {
            throw new IllegalArgumentException("Shipment monetary values are required");
        }
        validateRequiredAddress(request.sellerAddress(), "seller");
        validateRequiredAddress(request.buyerAddress(), "buyer");
        requireText(request.sellerAddress().phoneNumber(), "Seller phone number");
        requireText(request.buyerAddress().phoneNumber(), "Buyer phone number");
    }

    private void validateRequiredAddress(UserAddressResponseDTO address, String owner) {
        if (address == null) {
            throw new IllegalArgumentException("The " + owner + " shipping address is missing");
        }
        fullAddress(address);
    }

    private String contactName(UserAddressResponseDTO address) {
        return StringUtils.hasText(address.fullName()) ? address.fullName().trim() : address.userId();
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required for Viettel Post shipping");
        }
        return value.trim();
    }
}
