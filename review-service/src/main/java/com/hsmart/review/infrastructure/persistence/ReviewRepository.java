package com.hsmart.review.infrastructure.persistence;

import com.hsmart.review.domain.entities.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByOrderId(Long orderId);
    Page<Review> findAllBySellerIdAndHiddenFalseOrderByCreatedAtDesc(String sellerId, Pageable pageable);
    Page<Review> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
