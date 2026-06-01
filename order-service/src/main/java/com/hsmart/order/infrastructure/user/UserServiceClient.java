package com.hsmart.order.infrastructure.user;

import com.hsmart.order.application.dto.ApiResponse;
import com.hsmart.order.application.dto.UserAddressResponseDTO;
import com.hsmart.order.application.exceptions.ShippingAddressLookupUnavailableException;
import com.hsmart.order.service.UserClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class UserServiceClient implements UserClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient userServiceRestClient;
    private final String internalSharedSecret;

    public UserServiceClient(
            @Qualifier("userServiceRestClient") RestClient userServiceRestClient,
            @Value("${internal.security.secret:}") String internalSharedSecret
    ) {
        this.userServiceRestClient = userServiceRestClient;
        this.internalSharedSecret = internalSharedSecret;
    }

    @Override
    public UserAddressResponseDTO getUserAddress(String userId) {
        try {
            ApiResponse<UserAddressResponseDTO> response = userServiceRestClient.get()
                    .uri("/api/v1/users/internal/{userId}/address", userId)
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.getData() == null) {
                throw new ShippingAddressLookupUnavailableException("User-service returned an empty address response");
            }

            return response.getData();
        } catch (RestClientException exception) {
            log.warn("User-service address lookup failed for user {}", userId, exception);
            throw new ShippingAddressLookupUnavailableException("Shipping address lookup failed", exception);
        }
    }
}
