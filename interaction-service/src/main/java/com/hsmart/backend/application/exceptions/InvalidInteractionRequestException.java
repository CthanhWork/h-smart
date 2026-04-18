package com.hsmart.backend.application.exceptions;

public class InvalidInteractionRequestException extends RuntimeException {

    public InvalidInteractionRequestException(String message) {
        super(message);
    }
}
