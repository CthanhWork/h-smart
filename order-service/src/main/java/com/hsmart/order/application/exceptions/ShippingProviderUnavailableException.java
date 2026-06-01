package com.hsmart.order.application.exceptions;

public class ShippingProviderUnavailableException extends RuntimeException {
    public ShippingProviderUnavailableException(String message) {
        super(message);
    }

    public ShippingProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
