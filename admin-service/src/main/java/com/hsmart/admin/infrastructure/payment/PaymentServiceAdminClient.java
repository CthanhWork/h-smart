package com.hsmart.admin.infrastructure.payment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.admin.application.dto.ApiResponse;
import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.application.dto.SystemAccountSummaryDTO;
import com.hsmart.admin.application.dto.SystemLedgerEntryDTO;
import com.hsmart.admin.application.exceptions.MarketplaceStatsUnavailableException;
import com.hsmart.admin.service.PaymentAdminClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class PaymentServiceAdminClient implements PaymentAdminClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient paymentServiceRestClient;
    private final String internalSharedSecret;
    private final ObjectMapper objectMapper;

    public PaymentServiceAdminClient(
            @Qualifier("paymentServiceRestClient") RestClient paymentServiceRestClient,
            @Value("${internal.security.secret:}") String internalSharedSecret,
            ObjectMapper objectMapper
    ) {
        this.paymentServiceRestClient = paymentServiceRestClient;
        this.internalSharedSecret = internalSharedSecret;
        this.objectMapper = objectMapper;
    }

    @Override
    public SystemAccountSummaryDTO getSystemAccountSummary() {
        try {
            ApiResponse<Object> response = paymentServiceRestClient.get()
                    .uri("/api/v1/payments/internal/system-account")
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.getData() == null) {
                throw new MarketplaceStatsUnavailableException("payment-service returned an empty system-account summary", null);
            }
            return objectMapper.convertValue(response.getData(), SystemAccountSummaryDTO.class);
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("payment-service system-account summary request failed", exception);
            throw new MarketplaceStatsUnavailableException("System account summary is unavailable", exception);
        }
    }

    @Override
    public PageResponseDTO<SystemLedgerEntryDTO> listLedger(int page, int size) {
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/api/v1/payments/internal/system-account/ledger")
                    .queryParam("page", page)
                    .queryParam("size", size);

            ApiResponse<Object> response = paymentServiceRestClient.get()
                    .uri(uriBuilder.toUriString())
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.getData() == null) {
                return PageResponseDTO.<SystemLedgerEntryDTO>builder().content(java.util.List.of()).build();
            }
            return objectMapper.convertValue(
                    response.getData(),
                    new TypeReference<PageResponseDTO<SystemLedgerEntryDTO>>() {});
        } catch (RestClientException | IllegalArgumentException exception) {
            log.error("payment-service system-account ledger request failed", exception);
            throw new MarketplaceStatsUnavailableException("System account ledger is unavailable", exception);
        }
    }
}
