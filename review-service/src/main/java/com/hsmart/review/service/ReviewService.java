package com.hsmart.review.service;

import com.hsmart.review.application.dto.CreateReviewRequestDTO;
import com.hsmart.review.application.dto.ReviewResponseDTO;
import java.util.List;

public interface ReviewService {
    ReviewResponseDTO createReview(CreateReviewRequestDTO request, String buyerId);
    List<ReviewResponseDTO> getSellerReviews(String sellerId);
}
