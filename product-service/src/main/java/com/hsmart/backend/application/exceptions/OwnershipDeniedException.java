package com.hsmart.backend.application.exceptions;

public class OwnershipDeniedException extends RuntimeException {

    public OwnershipDeniedException() {
        super("You do not have permission to modify this product");
    }
}
