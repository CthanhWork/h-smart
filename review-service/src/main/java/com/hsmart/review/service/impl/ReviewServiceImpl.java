package com.hsmart.review.service.impl;

import com.hsmart.review.application.dto.CreateReviewRequestDTO;
import com.hsmart.review.application.dto.OrderResponseDTO;
import com.hsmart.review.application.dto.PageResponseDTO;
import com.hsmart.review.application.dto.PublicReviewResponseDTO;
import com.hsmart.review.application.dto.ReviewCreatedEvent;
import com.hsmart.review.application.dto.ReviewResponseDTO;
import com.hsmart.review.application.exceptions.DuplicateReviewException;
import com.hsmart.review.application.exceptions.OrderVerificationException;
import com.hsmart.review.application.exceptions.ReviewNotFoundException;
import com.hsmart.review.domain.entities.Review;
import com.hsmart.review.infrastructure.messaging.ReviewEventPublisher;
import com.hsmart.review.infrastructure.persistence.ReviewRepository;
import com.hsmart.review.service.OrderClient;
import com.hsmart.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
                .productId(order.productId())
                .buyerId(buyerId)
                .sellerId(order.sellerId())
                .rating(request.getRating())
                .comment(normalizeComment(request.getComment()))
                .build();

        Review savedReview = reviewRepository.save(review);
        ReviewCreatedEvent event = ReviewCreatedEvent.builder()
                .reviewId(savedReview.getId())
                .sellerId(savedReview.getSellerId())
                .rating(savedReview.getRating())
                .build();
        publishAfterCommit(() -> reviewEventPublisher.publishReviewCreated(event));

        log.info("Created review {} for seller {} from buyer {}", savedReview.getId(), savedReview.getSellerId(), buyerId);
        return toResponse(savedReview);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<PublicReviewResponseDTO> getSellerReviews(String sellerId, Pageable pageable) {
        Page<PublicReviewResponseDTO> page = reviewRepository
                .findAllBySellerIdAndHiddenFalseOrderByCreatedAtDesc(sellerId, pageable)
                .map(this::toPublicResponse);
        return PageResponseDTO.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ReviewResponseDTO> listAllReviewsForAdmin(Pageable pageable) {
        Page<Review> reviews = reviewRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PageResponseDTO.from(reviews.map(this::toResponse));
    }

    @Override
    public ReviewResponseDTO hideReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));
        review.setHidden(true);
        return toResponse(reviewRepository.save(review));
    }

    @Override
    public ReviewResponseDTO restoreReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));
        review.setHidden(false);
        return toResponse(reviewRepository.save(review));
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
                .productId(review.getProductId())
                .buyerId(review.getBuyerId())
                .sellerId(review.getSellerId())
                .rating(review.getRating())
                .comment(review.getComment())
                .hidden(review.isHidden())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    private PublicReviewResponseDTO toPublicResponse(Review review) {
        return PublicReviewResponseDTO.builder()
                .id(review.getId())
                .orderId(review.getOrderId())
                .productId(review.getProductId())
                .sellerId(review.getSellerId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
