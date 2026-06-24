package com.hsmart.review.application.exceptions;

public class ReviewNotFoundException extends RuntimeException {
    public ReviewNotFoundException(Long reviewId) {
        super("Review not found: " + reviewId);
    }
}
