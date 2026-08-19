package com.hsmart.review.presentation.controllers;

import com.hsmart.review.application.dto.ApiResponse;
import com.hsmart.review.application.dto.CreateReviewRequestDTO;
import com.hsmart.review.application.dto.PageResponseDTO;
import com.hsmart.review.application.dto.PublicReviewResponseDTO;
import com.hsmart.review.application.dto.ReviewResponseDTO;
import com.hsmart.review.application.exceptions.MissingUserContextException;
import com.hsmart.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponseDTO>> createReview(
            @Valid @RequestBody CreateReviewRequestDTO request,
            @RequestHeader(value = "X-User-Id", required = false) String buyerId
    ) {
        ReviewResponseDTO response = reviewService.createReview(request, requireUserId(buyerId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "Review created successfully", response));
    }

    @GetMapping("/sellers/{sellerId}")
    public ResponseEntity<ApiResponse<PageResponseDTO<PublicReviewResponseDTO>>> getSellerReviews(
            @PathVariable String sellerId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Seller reviews fetched successfully",
                reviewService.getSellerReviews(sellerId, pageable)
        ));
    }

    @GetMapping("/internal/admin/list")
    public ResponseEntity<ApiResponse<PageResponseDTO<ReviewResponseDTO>>> listAllReviewsForAdmin(
            Pageable pageable
    ) {
        PageResponseDTO<ReviewResponseDTO> response = reviewService.listAllReviewsForAdmin(pageable);
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Reviews fetched successfully",
                response
        ));
    }

    @PostMapping("/internal/{reviewId}/hide")
    public ResponseEntity<ApiResponse<ReviewResponseDTO>> hideReview(@PathVariable Long reviewId) {
        ReviewResponseDTO response = reviewService.hideReview(reviewId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Review hidden successfully", response));
    }

    @PostMapping("/internal/{reviewId}/restore")
    public ResponseEntity<ApiResponse<ReviewResponseDTO>> restoreReview(@PathVariable Long reviewId) {
        ReviewResponseDTO response = reviewService.restoreReview(reviewId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Review restored successfully", response));
    }

    private String requireUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new MissingUserContextException();
        }
        return userId.trim();
    }
}
