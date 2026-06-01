package com.hsmart.admin.application.exceptions;

public class MissingUserContextException extends RuntimeException {

    public MissingUserContextException() {
        super("Authenticated user context is required");
    }
}
