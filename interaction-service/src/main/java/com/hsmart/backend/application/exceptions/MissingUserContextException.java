package com.hsmart.backend.application.exceptions;

public class MissingUserContextException extends RuntimeException {

    public MissingUserContextException() {
        super("Missing X-User-Id header");
    }
}
