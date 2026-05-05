package com.hsmart.review.application.exceptions;

public class DuplicateReviewException extends RuntimeException {

    public DuplicateReviewException(Long orderId) {
        super("Order " + orderId + " has already been reviewed");
    }
}
