package com.hsmart.backend.infrastructure.exception;

public class AccountBannedException extends RuntimeException {

    public AccountBannedException() {
        super("Account has been banned");
    }
}
