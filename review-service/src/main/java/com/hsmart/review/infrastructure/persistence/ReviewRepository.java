package com.hsmart.review.infrastructure.persistence;

import com.hsmart.review.domain.entities.Review;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByOrderId(Long orderId);
    List<Review> findAllBySellerIdOrderByCreatedAtDesc(String sellerId);
}
