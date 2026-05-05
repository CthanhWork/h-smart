package com.hsmart.review.application.exceptions;

public class OrderVerificationUnavailableException extends RuntimeException {

    public OrderVerificationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
