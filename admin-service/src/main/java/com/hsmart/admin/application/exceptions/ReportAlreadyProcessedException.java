package com.hsmart.admin.application.exceptions;

public class ReportAlreadyProcessedException extends RuntimeException {

    public ReportAlreadyProcessedException(Long reportId) {
        super("Report has already been processed: " + reportId);
    }
}
