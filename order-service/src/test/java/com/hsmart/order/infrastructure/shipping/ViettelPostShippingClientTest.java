package com.hsmart.order.infrastructure.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.hsmart.order.application.dto.GhtkShipmentRequestDTO;
import com.hsmart.order.application.dto.UserAddressResponseDTO;
import com.hsmart.order.application.exceptions.ShippingProviderUnavailableException;
import com.hsmart.order.infrastructure.config.ViettelPostProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ViettelPostShippingClientTest {

    @Test
    void calculateShippingFeeShouldLoginAndParseMoneyTotal() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://partner2.viettelpost.vn");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ViettelPostShippingClient client = new ViettelPostShippingClient(builder.build(), properties(), new ObjectMapper());

        expectLogin(server);
        server.expect(requestTo("https://partner2.viettelpost.vn/v2/order/getPriceNlp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Token", "viettel-token"))
                .andExpect(content().json("""
                        {
                          "PRODUCT_WEIGHT": 1000,
                          "PRODUCT_PRICE": 0,
                          "MONEY_COLLECTION": 0,
                          "ORDER_SERVICE": "VCN",
                          "SENDER_FULLNAME": "seller-one",
                          "SENDER_PHONE": "0901234567",
                          "SENDER_ADDRESS": "1 Example Street, Ward 1, District 1, Ho Chi Minh City",
                          "SENDER_PROVINCE": "79",
                          "SENDER_DISTRICT": "760",
                          "SENDER_WARD": "26734",
                          "RECEIVER_FULLNAME": "buyer-one",
                          "RECEIVER_PHONE": "0901234567",
                          "RECEIVER_ADDRESS": "2 Buyer Street, Ward 2, Thu Duc City, Ho Chi Minh City",
                          "RECEIVER_PROVINCE": "79",
                          "RECEIVER_DISTRICT": "760",
                          "RECEIVER_WARD": "26734",
                          "PRODUCT_TYPE": "HH",
                          "NATIONAL_TYPE": 1
                        }
                        """, false))
                .andRespond(withSuccess(
                        "{\"status\":200,\"data\":{\"MONEY_TOTAL\":27000}}",
                        MediaType.APPLICATION_JSON
                ));

        BigDecimal fee = client.calculateShippingFee(
                address("seller-one", "District 1", "Ward 1", "1 Example Street"),
                address("buyer-one", "Thu Duc City", "Ward 2", "2 Buyer Street")
        );

        assertThat(fee).isEqualByComparingTo("27000");
        server.verify();
    }

    @Test
    void calculateShippingFeeShouldFallbackToMoneyTotalFee() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://partner2.viettelpost.vn");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ViettelPostShippingClient client = new ViettelPostShippingClient(builder.build(), properties(), new ObjectMapper());

        expectLogin(server);
        server.expect(requestTo("https://partner2.viettelpost.vn/v2/order/getPriceNlp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Token", "viettel-token"))
                .andRespond(withSuccess(
                        "{\"status\":200,\"data\":{\"MONEY_TOTAL_FEE\":27000}}",
                        MediaType.APPLICATION_JSON
                ));

        BigDecimal fee = client.calculateShippingFee(
                address("seller-one", "District 1", "Ward 1", "1 Example Street"),
                address("buyer-one", "Thu Duc City", "Ward 2", "2 Buyer Street")
        );

        assertThat(fee).isEqualByComparingTo("27000");
        server.verify();
    }

    @Test
    void createShipmentShouldLoginAndReturnOrderNumber() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://partner2.viettelpost.vn");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ViettelPostShippingClient client = new ViettelPostShippingClient(builder.build(), properties(), new ObjectMapper());

        expectLogin(server);
        server.expect(requestTo("https://partner2.viettelpost.vn/v2/order/createOrderNlp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Token", "viettel-token"))
                .andExpect(content().json("""
                        {
                          "ORDER_NUMBER": "5",
                          "SENDER_FULLNAME": "seller-one",
                          "SENDER_ADDRESS": "1 Example Street, Ward 1, District 1, Ho Chi Minh City",
                          "SENDER_PHONE": "0901234567",
                          "SENDER_PROVINCE": "79",
                          "SENDER_DISTRICT": "760",
                          "SENDER_WARD": "26734",
                          "RECEIVER_FULLNAME": "buyer-one",
                          "RECEIVER_ADDRESS": "2 Buyer Street, Ward 2, Thu Duc City, Ho Chi Minh City",
                          "RECEIVER_PHONE": "0901234567",
                          "RECEIVER_PROVINCE": "79",
                          "RECEIVER_DISTRICT": "760",
                          "RECEIVER_WARD": "26734",
                          "PRODUCT_NAME": "Rice cooker",
                          "PRODUCT_QUANTITY": 1,
                          "PRODUCT_PRICE": 100000,
                          "PRODUCT_WEIGHT": 1000,
                          "ORDER_PAYMENT": 3,
                          "ORDER_SERVICE": "VCN",
                          "PRODUCT_TYPE": "HH",
                          "MONEY_COLLECTION": 130000,
                          "CHECK_UNIQUE": true
                        }
                        """, false))
                .andRespond(withSuccess(
                        "{\"status\":200,\"data\":{\"ORDER_NUMBER\":\"VTP123456\"}}",
                        MediaType.APPLICATION_JSON
                ));

        String trackingCode = client.createShipment(new GhtkShipmentRequestDTO(
                "5",
                "Rice cooker",
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(130000),
                address("seller-one", "District 1", "Ward 1", "1 Example Street"),
                address("buyer-one", "Thu Duc City", "Ward 2", "2 Buyer Street")
        ));

        assertThat(trackingCode).isEqualTo("VTP123456");
        server.verify();
    }

    @Test
    void createShipmentShouldFailClearlyWhenCredentialsAreMissing() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://partner2.viettelpost.vn");
        ViettelPostShippingClient client = new ViettelPostShippingClient(
                builder.build(),
                new ViettelPostProperties(
                        "https://partner2.viettelpost.vn",
                        "",
                        "",
                        2000,
                        5000,
                        1000,
                        "VCN",
                        "",
                        "HH",
                        3,
                        true
                ),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> client.createShipment(new GhtkShipmentRequestDTO(
                "5",
                "Rice cooker",
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(130000),
                address("seller-one", "District 1", "Ward 1", "1 Example Street"),
                address("buyer-one", "Thu Duc City", "Ward 2", "2 Buyer Street")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("VIETTEL_POST_USERNAME is required for Viettel Post shipping");
    }

    @Test
    void calculateShippingFeeShouldWrapProviderResponseWithoutFee() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://partner2.viettelpost.vn");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ViettelPostShippingClient client = new ViettelPostShippingClient(builder.build(), properties(), new ObjectMapper());

        expectLogin(server);
        server.expect(requestTo("https://partner2.viettelpost.vn/v2/order/getPriceNlp"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"status\":500,\"message\":\"Fee is unavailable\"}",
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> client.calculateShippingFee(
                address("seller-one", "District 1", "Ward 1", "1 Example Street"),
                address("buyer-one", "Thu Duc City", "Ward 2", "2 Buyer Street")
        ))
                .isInstanceOf(ShippingProviderUnavailableException.class)
                .hasMessage("Viettel Post shipping fee calculation failed");
        server.verify();
    }

    private void expectLogin(MockRestServiceServer server) {
        server.expect(requestTo("https://partner2.viettelpost.vn/v2/user/Login"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "USERNAME": "viettel-user",
                          "PASSWORD": "viettel-password"
                        }
                        """, false))
                .andRespond(withSuccess(
                        "{\"status\":200,\"data\":{\"TOKEN\":\"viettel-token\"}}",
                        MediaType.APPLICATION_JSON
                ));
    }

    private UserAddressResponseDTO address(String userId, String district, String ward, String streetDetail) {
        return new UserAddressResponseDTO(
                userId,
                userId,
                "0901234567",
                "79",
                "Ho Chi Minh City",
                "760",
                district,
                "26734",
                ward,
                streetDetail
        );
    }

    private ViettelPostProperties properties() {
        return new ViettelPostProperties(
                "https://partner2.viettelpost.vn",
                "viettel-user",
                "viettel-password",
                2000,
                5000,
                1000,
                "VCN",
                "",
                "HH",
                3,
                true
        );
    }
}
