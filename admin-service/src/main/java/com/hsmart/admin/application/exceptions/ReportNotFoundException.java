package com.hsmart.admin.application.exceptions;

public class ReportNotFoundException extends RuntimeException {

    public ReportNotFoundException(Long reportId) {
        super("Report not found: " + reportId);
    }
}
