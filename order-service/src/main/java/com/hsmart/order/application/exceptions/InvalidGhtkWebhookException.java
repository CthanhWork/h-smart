package com.hsmart.order.application.exceptions;

public class InvalidGhtkWebhookException extends RuntimeException {
    public InvalidGhtkWebhookException(String message) {
        super(message);
    }
}
