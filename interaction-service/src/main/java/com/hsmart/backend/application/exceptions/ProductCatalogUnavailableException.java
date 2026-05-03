package com.hsmart.backend.application.exceptions;

public class ProductCatalogUnavailableException extends RuntimeException {

    public ProductCatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
