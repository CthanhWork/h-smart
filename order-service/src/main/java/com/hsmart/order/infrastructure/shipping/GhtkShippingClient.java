package com.hsmart.order.infrastructure.shipping;

import com.hsmart.order.application.dto.GhtkFeeResponseDTO;
import com.hsmart.order.application.dto.GhtkCreateOrderRequestDTO;
import com.hsmart.order.application.dto.GhtkCreateOrderResponseDTO;
import com.hsmart.order.application.dto.GhtkShipmentRequestDTO;
import com.hsmart.order.application.dto.UserAddressResponseDTO;
import com.hsmart.order.application.exceptions.ShippingProviderUnavailableException;
import com.hsmart.order.infrastructure.config.GhtkProperties;
import com.hsmart.order.service.GhtkClient;
import java.math.BigDecimal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

@Slf4j
@Component
public class GhtkShippingClient implements GhtkClient {

    private static final String TOKEN_HEADER = "Token";
    private static final String CLIENT_SOURCE_HEADER = "X-Client-Source";
    private static final String DUPLICATE_ORDER_CODE = "ORDER_ID_EXIST";

    private final RestClient ghtkRestClient;
    private final GhtkProperties properties;

    public GhtkShippingClient(
            @Qualifier("ghtkRestClient") RestClient ghtkRestClient,
            GhtkProperties properties
    ) {
        this.ghtkRestClient = ghtkRestClient;
        this.properties = properties;
    }

    @Override
    public BigDecimal calculateShippingFee(UserAddressResponseDTO sellerAddress, UserAddressResponseDTO buyerAddress) {
        validateConfiguration();
        validateRequiredAddress(sellerAddress, "seller");
        validateRequiredAddress(buyerAddress, "buyer");

        RestClient.RequestHeadersUriSpec<?> feeRequest = ghtkRestClient.get();
        addHeaders(feeRequest);
        GhtkFeeResponseDTO response = feeRequest
                .uri(uriBuilder -> buildFeeUri(uriBuilder, sellerAddress, buyerAddress))
                .retrieve()
                .body(GhtkFeeResponseDTO.class);

        if (response == null || !response.success() || response.fee() == null || response.fee().fee() == null) {
            throw new IllegalStateException("GHTK returned an invalid shipping fee response");
        }

        return response.fee().fee();
    }

    @Override
    public String createShipment(GhtkShipmentRequestDTO request) {
        validateConfiguration();
        validateShipmentRequest(request);

        try {
            RestClient.RequestBodyUriSpec shipmentRequest = ghtkRestClient.post();
            addHeaders(shipmentRequest);
            GhtkCreateOrderResponseDTO response = shipmentRequest
                    .uri("/services/shipment/order")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toGhtkRequest(request))
                    .retrieve()
                    .body(GhtkCreateOrderResponseDTO.class);

            if (response != null && response.success() && response.order() != null
                    && StringUtils.hasText(response.order().label())) {
                return response.order().label();
            }

            if (response != null && response.error() != null
                    && DUPLICATE_ORDER_CODE.equals(response.error().code())
                    && StringUtils.hasText(response.error().ghtkLabel())) {
                log.info("Using existing GHTK tracking code for partner order {}", request.partnerOrderId());
                return response.error().ghtkLabel();
            }

            throw new ShippingProviderUnavailableException("GHTK returned an invalid shipment creation response");
        } catch (RestClientException exception) {
            log.warn("GHTK shipment creation failed for partner order {}", request.partnerOrderId(), exception);
            throw new ShippingProviderUnavailableException("GHTK shipment creation failed", exception);
        }
    }

    private java.net.URI buildFeeUri(
            UriBuilder uriBuilder,
            UserAddressResponseDTO sellerAddress,
            UserAddressResponseDTO buyerAddress
    ) {
        uriBuilder.path("/services/shipment/fee")
                .queryParam("pick_province", sellerAddress.province())
                .queryParam("pick_district", sellerAddress.district())
                .queryParam("province", buyerAddress.province())
                .queryParam("district", buyerAddress.district())
                .queryParam("weight", properties.defaultWeightGrams());

        addQueryParamIfPresent(uriBuilder, "pick_address", sellerAddress.streetDetail());
        addQueryParamIfPresent(uriBuilder, "pick_ward", sellerAddress.ward());
        addQueryParamIfPresent(uriBuilder, "address", buyerAddress.streetDetail());
        addQueryParamIfPresent(uriBuilder, "ward", buyerAddress.ward());
        return uriBuilder.build();
    }

    private void addQueryParamIfPresent(UriBuilder uriBuilder, String name, String value) {
        if (StringUtils.hasText(value)) {
            uriBuilder.queryParam(name, value);
        }
    }

    private void addHeaders(RestClient.RequestHeadersSpec<?> request) {
        request.header(TOKEN_HEADER, properties.apiToken());
        if (StringUtils.hasText(properties.clientSource())) {
            request.header(CLIENT_SOURCE_HEADER, properties.clientSource());
        }
    }

    private GhtkCreateOrderRequestDTO toGhtkRequest(GhtkShipmentRequestDTO request) {
        UserAddressResponseDTO seller = request.sellerAddress();
        UserAddressResponseDTO buyer = request.buyerAddress();
        BigDecimal productWeightKg = BigDecimal.valueOf(properties.defaultWeightGrams()).movePointLeft(3);

        GhtkCreateOrderRequestDTO.Product product = new GhtkCreateOrderRequestDTO.Product(
                request.productName(),
                request.productValue(),
                productWeightKg,
                1,
                request.partnerOrderId()
        );
        GhtkCreateOrderRequestDTO.Order order = new GhtkCreateOrderRequestDTO.Order(
                request.partnerOrderId(),
                contactName(seller),
                seller.streetDetail(),
                seller.province(),
                seller.district(),
                seller.ward(),
                seller.phoneNumber(),
                contactName(buyer),
                buyer.streetDetail(),
                buyer.province(),
                buyer.district(),
                buyer.ward(),
                buyer.streetDetail(),
                null,
                buyer.phoneNumber(),
                1,
                request.codAmount(),
                request.productValue()
        );
        return new GhtkCreateOrderRequestDTO(List.of(product), order);
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.apiToken())) {
            throw new ShippingProviderUnavailableException("GHTK_API_TOKEN is not configured");
        }
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
        requireText(request.sellerAddress().streetDetail(), "Seller street detail");
        requireText(request.sellerAddress().phoneNumber(), "Seller phone number");
        requireText(request.buyerAddress().streetDetail(), "Buyer street detail");
        requireText(request.buyerAddress().ward(), "Buyer ward");
        requireText(request.buyerAddress().phoneNumber(), "Buyer phone number");
    }

    private void validateRequiredAddress(UserAddressResponseDTO address, String owner) {
        if (address == null || !StringUtils.hasText(address.province()) || !StringUtils.hasText(address.district())) {
            throw new IllegalArgumentException("The " + owner + " shipping address is missing province or district");
        }
    }

    private String contactName(UserAddressResponseDTO address) {
        return StringUtils.hasText(address.fullName()) ? address.fullName().trim() : address.userId();
    }

    private void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required for GHTK shipment creation");
        }
    }
}
