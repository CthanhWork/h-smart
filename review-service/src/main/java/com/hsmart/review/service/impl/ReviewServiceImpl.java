package com.hsmart.review.service.impl;

import com.hsmart.review.application.dto.CreateReviewRequestDTO;
import com.hsmart.review.application.dto.OrderResponseDTO;
import com.hsmart.review.application.dto.ReviewCreatedEvent;
import com.hsmart.review.application.dto.ReviewResponseDTO;
import com.hsmart.review.application.exceptions.DuplicateReviewException;
import com.hsmart.review.application.exceptions.OrderVerificationException;
import com.hsmart.review.domain.entities.Review;
import com.hsmart.review.infrastructure.messaging.ReviewEventPublisher;
import com.hsmart.review.infrastructure.persistence.ReviewRepository;
import com.hsmart.review.service.OrderClient;
import com.hsmart.review.service.ReviewService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ReviewServiceImpl implements ReviewService {

    private static final String COMPLETED_STATUS = "COMPLETED";

    private final ReviewRepository reviewRepository;
    private final OrderClient orderClient;
    private final ReviewEventPublisher reviewEventPublisher;

    @Override
    public ReviewResponseDTO createReview(CreateReviewRequestDTO request, String buyerId) {
        if (reviewRepository.existsByOrderId(request.getOrderId())) {
            throw new DuplicateReviewException(request.getOrderId());
        }

        OrderResponseDTO order = orderClient.getOrder(request.getOrderId(), buyerId);
        validateOrderCanBeReviewed(order, buyerId);

        Review review = Review.builder()
                .orderId(order.id())
                .buyerId(buyerId)
                .sellerId(order.sellerId())
                .rating(request.getRating())
                .comment(normalizeComment(request.getComment()))
                .build();

        Review savedReview = reviewRepository.save(review);
        ReviewCreatedEvent event = ReviewCreatedEvent.builder()
                .sellerId(savedReview.getSellerId())
                .rating(savedReview.getRating())
                .build();
        publishAfterCommit(() -> reviewEventPublisher.publishReviewCreated(event));

        log.info("Created review {} for seller {} from buyer {}", savedReview.getId(), savedReview.getSellerId(), buyerId);
        return toResponse(savedReview);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewResponseDTO> getSellerReviews(String sellerId) {
        return reviewRepository.findAllBySellerIdOrderByCreatedAtDesc(sellerId).stream()
                .map(this::toResponse)
                .toList();
    }

    private void validateOrderCanBeReviewed(OrderResponseDTO order, String buyerId) {
        if (!COMPLETED_STATUS.equalsIgnoreCase(order.status())) {
            throw new OrderVerificationException("Only completed orders can be reviewed");
        }

        if (!buyerId.equals(order.buyerId())) {
            throw new OrderVerificationException("Only the order buyer can review this order");
        }

        if (!StringUtils.hasText(order.sellerId())) {
            throw new OrderVerificationException("Order seller information is missing");
        }

        if (buyerId.equals(order.sellerId())) {
            throw new OrderVerificationException("Buyers cannot review themselves");
        }
    }

    private String normalizeComment(String comment) {
        if (!StringUtils.hasText(comment)) {
            return null;
        }
        return comment.trim();
    }

    private void publishAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private ReviewResponseDTO toResponse(Review review) {
        return ReviewResponseDTO.builder()
                .id(review.getId())
                .orderId(review.getOrderId())
                .buyerId(review.getBuyerId())
                .sellerId(review.getSellerId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
