package com.hsmart.admin.infrastructure.user;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.admin.application.dto.ApiResponse;
import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.application.dto.SellerTrustResponseDTO;
import com.hsmart.admin.application.dto.UserAdminSummaryDTO;
import com.hsmart.admin.application.dto.UserStatusUpdateRequestDTO;
import com.hsmart.admin.application.exceptions.UserTrustLookupException;
import com.hsmart.admin.application.exceptions.UserStatusUpdateException;
import com.hsmart.admin.service.UserAdminClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

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

    @Override
    public void updateUserActiveStatus(String userId, boolean active) {
        try {
            userServiceRestClient.put()
                    .uri("/api/v1/users/internal/{userId}/status", userId)
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .body(new UserStatusUpdateRequestDTO(active))
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<Object>>() {
                    });
            log.info("User-service active status update completed for user {}", userId);
        } catch (RestClientException exception) {
            log.error("User-service active status update failed for user {}", userId, exception);
            throw new UserStatusUpdateException("User active status update failed", exception);
        }
    }

    @Override
    public PageResponseDTO<UserAdminSummaryDTO> listUsers(String search, Boolean isActive, Pageable pageable) {
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/api/v1/users/internal/admin/list")
                    .queryParam("page", pageable.getPageNumber())
                    .queryParam("size", pageable.getPageSize());
            if (search != null && !search.isBlank()) {
                uriBuilder.queryParam("search", search);
            }
            if (isActive != null) {
                uriBuilder.queryParam("isActive", isActive);
            }

            ApiResponse<Object> response = userServiceRestClient.get()
                    .uri(uriBuilder.toUriString())
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.getData() == null) {
                return PageResponseDTO.<UserAdminSummaryDTO>builder().content(java.util.List.of()).build();
            }

            PageResponseDTO<UserAdminSummaryDTO> page = objectMapper.convertValue(
                    response.getData(),
                    new TypeReference<PageResponseDTO<UserAdminSummaryDTO>>() {}
            );
            log.info("User-service list users completed, page {}", pageable.getPageNumber());
            return page;
        } catch (RestClientException | IllegalArgumentException exception) {
            log.error("User-service list users failed", exception);
            throw new com.hsmart.admin.application.exceptions.MarketplaceStatsUnavailableException("User list unavailable", exception);
        }
    }

    @Override
    public UserAdminSummaryDTO getUserById(Long userId) {
        try {
            ApiResponse<Object> response = userServiceRestClient.get()
                    .uri("/api/v1/users/internal/admin/{userId}", userId)
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.getData() == null) {
                throw new UserTrustLookupException("User-service returned empty user response", null);
            }
            UserAdminSummaryDTO user = objectMapper.convertValue(response.getData(), UserAdminSummaryDTO.class);
            log.info("User-service get user by id completed for user {}", userId);
            return user;
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("User-service get user by id failed for user {}", userId, exception);
            throw new UserTrustLookupException("User lookup failed", exception);
        }
    }
}
