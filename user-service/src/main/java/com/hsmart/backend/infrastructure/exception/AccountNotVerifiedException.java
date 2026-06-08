package com.hsmart.backend.infrastructure.exception;

public class AccountNotVerifiedException extends RuntimeException {
    public AccountNotVerifiedException() {
        super("Email address is not verified");
    }
}
