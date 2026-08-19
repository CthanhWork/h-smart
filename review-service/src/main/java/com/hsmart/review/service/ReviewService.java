package com.hsmart.review.service;

import com.hsmart.review.application.dto.CreateReviewRequestDTO;
import com.hsmart.review.application.dto.PageResponseDTO;
import com.hsmart.review.application.dto.PublicReviewResponseDTO;
import com.hsmart.review.application.dto.ReviewResponseDTO;
import org.springframework.data.domain.Pageable;

public interface ReviewService {
    ReviewResponseDTO createReview(CreateReviewRequestDTO request, String buyerId);
    PageResponseDTO<PublicReviewResponseDTO> getSellerReviews(String sellerId, Pageable pageable);
    PageResponseDTO<ReviewResponseDTO> listAllReviewsForAdmin(Pageable pageable);
    ReviewResponseDTO hideReview(Long reviewId);
    ReviewResponseDTO restoreReview(Long reviewId);
}
