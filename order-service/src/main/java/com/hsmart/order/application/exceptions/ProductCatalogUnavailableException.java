package com.hsmart.order.application.exceptions;

public class ProductCatalogUnavailableException extends RuntimeException {

    public ProductCatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
