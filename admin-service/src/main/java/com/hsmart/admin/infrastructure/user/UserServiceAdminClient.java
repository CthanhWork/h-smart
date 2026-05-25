package com.hsmart.admin.infrastructure.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.admin.application.dto.ApiResponse;
import com.hsmart.admin.application.dto.SellerTrustResponseDTO;
import com.hsmart.admin.application.exceptions.UserTrustLookupException;
import com.hsmart.admin.service.UserAdminClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class UserServiceAdminClient implements UserAdminClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient userServiceRestClient;
    private final String internalSharedSecret;
    private final ObjectMapper objectMapper;

    public UserServiceAdminClient(
            @Qualifier("userServiceRestClient") RestClient userServiceRestClient,
            @Value("${internal.security.secret:}") String internalSharedSecret,
            ObjectMapper objectMapper
    ) {
        this.userServiceRestClient = userServiceRestClient;
        this.internalSharedSecret = internalSharedSecret;
        this.objectMapper = objectMapper;
    }

    @Override
    public SellerTrustResponseDTO getSellerTrustProfile(String sellerId) {
        try {
            ApiResponse<Object> response = userServiceRestClient.get()
                    .uri("/api/v1/users/internal/{sellerId}/trust", sellerId)
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.getData() == null) {
                throw new UserTrustLookupException("User-service returned an empty seller trust response", null);
            }

            SellerTrustResponseDTO trustProfile = objectMapper.convertValue(response.getData(), SellerTrustResponseDTO.class);
            log.info("User-service seller trust lookup completed for seller {}", sellerId);
            return trustProfile;
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("User-service seller trust request failed for seller {}", sellerId, exception);
            throw new UserTrustLookupException("Seller trust lookup failed", exception);
        }
    }
}
