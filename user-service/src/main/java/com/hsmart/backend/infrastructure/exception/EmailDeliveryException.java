package com.hsmart.backend.infrastructure.exception;

public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(Throwable cause) {
        super("Email service is temporarily unavailable", cause);
    }
}
