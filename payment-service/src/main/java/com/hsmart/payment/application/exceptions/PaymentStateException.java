package com.hsmart.payment.application.exceptions;

public class PaymentStateException extends RuntimeException {
    public PaymentStateException(String message) {
        super(message);
    }
}
