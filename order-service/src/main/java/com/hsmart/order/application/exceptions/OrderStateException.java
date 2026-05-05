package com.hsmart.order.application.exceptions;

public class OrderStateException extends RuntimeException {

    public OrderStateException(String message) {
        super(message);
    }
}
