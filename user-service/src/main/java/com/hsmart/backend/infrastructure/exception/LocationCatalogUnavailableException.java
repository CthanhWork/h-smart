package com.hsmart.backend.infrastructure.exception;

public class LocationCatalogUnavailableException extends RuntimeException {
    public LocationCatalogUnavailableException(String message) {
        super(message);
    }

    public LocationCatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
