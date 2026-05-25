package com.hsmart.backend.application.exceptions;

public class AiServiceTimeoutException extends RuntimeException {

    public AiServiceTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
