package com.hsmart.payment.application.exceptions;

public class InvalidPaymentCallbackException extends RuntimeException {
    public InvalidPaymentCallbackException(String message) {
        super(message);
    }
}
