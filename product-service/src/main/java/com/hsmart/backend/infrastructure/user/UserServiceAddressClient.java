package com.hsmart.backend.infrastructure.user;

import com.hsmart.backend.application.dto.ApiResponse;
import com.hsmart.backend.application.dto.UserAddressResponseDTO;
import com.hsmart.backend.service.UserAddressClient;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class UserServiceAddressClient implements UserAddressClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestTemplate restTemplate;

    @Value("${services.user.base-url:http://localhost:8081}")
    private String userServiceBaseUrl;

    @Value("${internal.security.secret:}")
    private String internalSharedSecret;

    public UserServiceAddressClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public Optional<UserAddressResponseDTO> getUserAddress(String userId) {
        if (!StringUtils.hasText(userId)) {
            return Optional.empty();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            if (StringUtils.hasText(internalSharedSecret)) {
                headers.set(INTERNAL_SECRET_HEADER, internalSharedSecret);
            }
            ResponseEntity<ApiResponse<UserAddressResponseDTO>> response = restTemplate.exchange(
                    trimTrailingSlash(userServiceBaseUrl) + "/api/v1/users/internal/{userId}/address",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {
                    },
                    userId
            );
            ApiResponse<UserAddressResponseDTO> body = response.getBody();
            return Optional.ofNullable(body == null ? null : body.getData());
        } catch (RestClientException exception) {
            log.warn("User-service address lookup failed for seller {}", userId, exception);
            return Optional.empty();
        }
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
