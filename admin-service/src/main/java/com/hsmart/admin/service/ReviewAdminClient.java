package com.hsmart.admin.service;

import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.application.dto.ReviewAdminSummaryDTO;
import org.springframework.data.domain.Pageable;

public interface ReviewAdminClient {
    PageResponseDTO<ReviewAdminSummaryDTO> listAllReviews(Pageable pageable);
    ReviewAdminSummaryDTO hideReview(Long reviewId);
    ReviewAdminSummaryDTO restoreReview(Long reviewId);
}
