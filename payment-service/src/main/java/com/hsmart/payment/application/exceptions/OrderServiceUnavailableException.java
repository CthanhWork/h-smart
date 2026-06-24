package com.hsmart.payment.application.exceptions;

public class OrderServiceUnavailableException extends RuntimeException {
    public OrderServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public OrderServiceUnavailableException(String message) {
        super(message);
    }
}
