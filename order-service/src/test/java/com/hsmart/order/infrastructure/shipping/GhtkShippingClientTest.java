package com.hsmart.order.infrastructure.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.hsmart.order.application.dto.GhtkShipmentRequestDTO;
import com.hsmart.order.application.dto.UserAddressResponseDTO;
import com.hsmart.order.infrastructure.config.GhtkProperties;
import com.hsmart.order.application.exceptions.ShippingProviderUnavailableException;
import java.math.BigDecimal;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GhtkShippingClientTest {

    @Test
    void calculateShippingFeeShouldSendRequiredGhtkRequestAndParseFee() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://services.giaohangtietkiem.vn");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GhtkShippingClient client = new GhtkShippingClient(
                builder.build(),
                properties()
        );
        server.expect(request -> assertFeeRequest(request.getURI()))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Token", "ghtk-token"))
                .andRespond(withSuccess(
                        "{\"success\":true,\"message\":\"\",\"fee\":{\"name\":\"area1\",\"fee\":30400,\"insurance_fee\":15000,\"delivery\":true}}",
                        MediaType.APPLICATION_JSON
                ));

        BigDecimal fee = client.calculateShippingFee(
                address("seller-one", "District 1"),
                address("buyer-one", "Thu Duc City")
        );

        assertThat(fee).isEqualByComparingTo("30400");
        server.verify();
    }

    @Test
    void createShipmentShouldSendGhtkOrderPayloadAndReturnTrackingCode() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://services.giaohangtietkiem.vn");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GhtkShippingClient client = new GhtkShippingClient(builder.build(), properties());
        server.expect(requestTo("https://services.giaohangtietkiem.vn/services/shipment/order"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Token", "ghtk-token"))
                .andExpect(header("X-Client-Source", "HSMART"))
                .andExpect(content().json("""
                        {
                          "products": [{
                            "name": "Rice cooker",
                            "price": 100000,
                            "weight": 1.000,
                            "quantity": 1,
                            "product_code": "5"
                          }],
                          "order": {
                            "id": "5",
                            "pick_name": "seller-one",
                            "pick_address": "1 Example Street",
                            "pick_province": "Ho Chi Minh City",
                            "pick_district": "District 1",
                            "pick_ward": "Ward 1",
                            "pick_tel": "0901234567",
                            "name": "buyer-one",
                            "address": "1 Example Street",
                            "province": "Ho Chi Minh City",
                            "district": "Thu Duc City",
                            "ward": "Ward 1",
                            "street": "1 Example Street",
                            "tel": "0901234567",
                            "is_freeship": 1,
                            "pick_money": 130000,
                            "value": 100000
                          }
                        }
                        """, false))
                .andRespond(withSuccess(
                        "{\"success\":true,\"message\":\"\",\"order\":{\"label\":\"S1.A1.12345\"}}",
                        MediaType.APPLICATION_JSON
                ));

        String trackingCode = client.createShipment(new GhtkShipmentRequestDTO(
                "5",
                "Rice cooker",
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(130000),
                address("seller-one", "District 1"),
                address("buyer-one", "Thu Duc City")
        ));

        assertThat(trackingCode).isEqualTo("S1.A1.12345");
        server.verify();
    }

    @Test
    void createShipmentShouldReuseTrackingCodeWhenGhtkReportsDuplicatePartnerOrder() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://services.giaohangtietkiem.vn");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GhtkShippingClient client = new GhtkShippingClient(builder.build(), properties());
        server.expect(requestTo("https://services.giaohangtietkiem.vn/services/shipment/order"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                          "success": false,
                          "message": "Partner order already exists",
                          "error": {
                            "code": "ORDER_ID_EXIST",
                            "ghtk_label": "S1.A1.12345"
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        String trackingCode = client.createShipment(new GhtkShipmentRequestDTO(
                "5",
                "Rice cooker",
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(130000),
                address("seller-one", "District 1"),
                address("buyer-one", "Thu Duc City")
        ));

        assertThat(trackingCode).isEqualTo("S1.A1.12345");
        server.verify();
    }

    @Test
    void createShipmentShouldFailClearlyWhenApiTokenIsMissing() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://services.giaohangtietkiem.vn");
        GhtkShippingClient client = new GhtkShippingClient(
                builder.build(),
                new GhtkProperties(
                        "https://services.giaohangtietkiem.vn",
                        "",
                        2000,
                        5000,
                        1000,
                        "HSMART",
                        "webhook-secret"
                )
        );

        assertThatThrownBy(() -> client.createShipment(new GhtkShipmentRequestDTO(
                "5",
                "Rice cooker",
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(130000),
                address("seller-one", "District 1"),
                address("buyer-one", "Thu Duc City")
        )))
                .isInstanceOf(ShippingProviderUnavailableException.class)
                .hasMessage("GHTK_API_TOKEN is not configured");
    }

    private void assertFeeRequest(URI uri) {
        String request = uri.toString();
        assertThat(request).contains("/services/shipment/fee?");
        assertThat(request).contains("pick_province=Ho%20Chi%20Minh%20City");
        assertThat(request).contains("pick_district=District%201");
        assertThat(request).contains("province=Ho%20Chi%20Minh%20City");
        assertThat(request).contains("district=Thu%20Duc%20City");
        assertThat(request).contains("weight=1000");
    }

    private UserAddressResponseDTO address(String userId, String district) {
        return new UserAddressResponseDTO(
                userId,
                userId,
                "0901234567",
                "79",
                "Ho Chi Minh City",
                "760",
                district,
                "26734",
                "Ward 1",
                "1 Example Street"
        );
    }

    private GhtkProperties properties() {
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
