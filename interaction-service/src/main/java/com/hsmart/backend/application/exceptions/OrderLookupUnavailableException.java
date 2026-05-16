package com.hsmart.backend.application.exceptions;

public class OrderLookupUnavailableException extends RuntimeException {

    public OrderLookupUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
