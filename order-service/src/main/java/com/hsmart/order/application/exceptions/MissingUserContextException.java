package com.hsmart.order.application.exceptions;

public class MissingUserContextException extends RuntimeException {

    public MissingUserContextException() {
        super("Missing authenticated user context");
    }
}
