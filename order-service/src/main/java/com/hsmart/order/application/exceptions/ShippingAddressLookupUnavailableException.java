package com.hsmart.order.application.exceptions;

public class ShippingAddressLookupUnavailableException extends RuntimeException {
    public ShippingAddressLookupUnavailableException(String message) {
        super(message);
    }

    public ShippingAddressLookupUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
