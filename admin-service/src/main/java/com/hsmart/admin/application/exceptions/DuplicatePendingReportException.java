package com.hsmart.admin.application.exceptions;

public class DuplicatePendingReportException extends RuntimeException {

    public DuplicatePendingReportException(Long productId) {
        super("You already have a pending report for product " + productId);
    }
}
