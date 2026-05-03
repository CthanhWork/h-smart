package com.hsmart.backend.application.exceptions;

public class AssistantServiceUnavailableException extends RuntimeException {

    public AssistantServiceUnavailableException(String message) {
        super(message);
    }

    public AssistantServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
