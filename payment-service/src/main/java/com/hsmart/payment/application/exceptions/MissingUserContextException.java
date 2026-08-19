package com.hsmart.payment.application.exceptions;

public class MissingUserContextException extends RuntimeException {
    public MissingUserContextException() {
        super("Authenticated user context is required");
    }
}
