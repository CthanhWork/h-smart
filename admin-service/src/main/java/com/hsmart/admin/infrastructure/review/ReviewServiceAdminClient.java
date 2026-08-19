package com.hsmart.admin.infrastructure.review;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.admin.application.dto.ApiResponse;
import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.application.dto.ReviewAdminSummaryDTO;
import com.hsmart.admin.application.exceptions.MarketplaceStatsUnavailableException;
import com.hsmart.admin.service.ReviewAdminClient;
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
public class ReviewServiceAdminClient implements ReviewAdminClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient reviewServiceRestClient;
    private final String internalSharedSecret;
    private final ObjectMapper objectMapper;

    public ReviewServiceAdminClient(
            @Qualifier("reviewServiceRestClient") RestClient reviewServiceRestClient,
            @Value("${internal.security.secret:}") String internalSharedSecret,
            ObjectMapper objectMapper
    ) {
        this.reviewServiceRestClient = reviewServiceRestClient;
        this.internalSharedSecret = internalSharedSecret;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResponseDTO<ReviewAdminSummaryDTO> listAllReviews(Pageable pageable) {
        try {
            String uri = UriComponentsBuilder.fromPath("/api/v1/reviews/internal/admin/list")
                    .queryParam("page", pageable.getPageNumber())
                    .queryParam("size", pageable.getPageSize())
                    .toUriString();

            ApiResponse<Object> response = reviewServiceRestClient.get()
                    .uri(uri)
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.getData() == null) {
                return PageResponseDTO.<ReviewAdminSummaryDTO>builder().content(java.util.List.of()).build();
            }

            PageResponseDTO<ReviewAdminSummaryDTO> page = objectMapper.convertValue(
                    response.getData(),
                    new TypeReference<PageResponseDTO<ReviewAdminSummaryDTO>>() {}
            );
            log.info("Review-service list all reviews completed, page {}", pageable.getPageNumber());
            return page;
        } catch (RestClientException | IllegalArgumentException exception) {
            log.error("Review-service list all reviews failed", exception);
            throw new MarketplaceStatsUnavailableException("Review list unavailable", exception);
        }
    }

    @Override
    public ReviewAdminSummaryDTO hideReview(Long reviewId) {
        return postReviewAction(reviewId, "hide");
    }

    @Override
    public ReviewAdminSummaryDTO restoreReview(Long reviewId) {
        return postReviewAction(reviewId, "restore");
    }

    private ReviewAdminSummaryDTO postReviewAction(Long reviewId, String action) {
        try {
            ApiResponse<Object> response = reviewServiceRestClient.post()
                    .uri("/api/v1/reviews/internal/{reviewId}/{action}", reviewId, action)
                    .header(INTERNAL_SECRET_HEADER, internalSharedSecret)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.getData() == null) {
                throw new MarketplaceStatsUnavailableException("Review-service returned empty response for " + action, null);
            }
            ReviewAdminSummaryDTO review = objectMapper.convertValue(response.getData(), ReviewAdminSummaryDTO.class);
            log.info("Review-service {} completed for review {}", action, reviewId);
            return review;
        } catch (RestClientException | IllegalArgumentException exception) {
            log.error("Review-service {} failed for review {}", action, reviewId, exception);
            throw new MarketplaceStatsUnavailableException("Review " + action + " failed", exception);
        }
    }
}
