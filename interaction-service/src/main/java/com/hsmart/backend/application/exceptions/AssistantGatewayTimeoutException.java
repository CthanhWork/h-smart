package com.hsmart.backend.application.exceptions;

public class AssistantGatewayTimeoutException extends RuntimeException {

    public AssistantGatewayTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
