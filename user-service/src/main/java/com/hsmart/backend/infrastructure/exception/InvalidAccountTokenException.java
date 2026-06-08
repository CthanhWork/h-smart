package com.hsmart.backend.infrastructure.exception;

public class InvalidAccountTokenException extends RuntimeException {
    public InvalidAccountTokenException() {
        super("Account token is invalid or expired");
    }
}
