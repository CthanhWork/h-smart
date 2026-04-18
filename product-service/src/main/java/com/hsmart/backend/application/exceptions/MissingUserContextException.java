package com.hsmart.backend.application.exceptions;

public class MissingUserContextException extends RuntimeException {

    public MissingUserContextException() {
        super("Authenticated user context is missing");
    }
}
