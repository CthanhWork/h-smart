package com.hsmart.backend.application.exceptions;

public class DuplicateCategoryException extends RuntimeException {

    public DuplicateCategoryException(String categoryName) {
        super("Category already exists: " + categoryName);
    }
}
