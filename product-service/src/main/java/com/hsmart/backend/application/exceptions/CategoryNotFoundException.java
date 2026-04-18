package com.hsmart.backend.application.exceptions;

public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(Long categoryId) {
        super("Category not found: " + categoryId);
    }
}
