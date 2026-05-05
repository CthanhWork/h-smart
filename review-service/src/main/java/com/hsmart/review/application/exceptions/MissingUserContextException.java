package com.hsmart.review.application.exceptions;

public class MissingUserContextException extends RuntimeException {

    public MissingUserContextException() {
        super("Missing authenticated user context");
    }
}
