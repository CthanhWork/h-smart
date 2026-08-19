package com.hsmart.admin.infrastructure.exception;

import com.hsmart.admin.application.dto.ApiResponse;
import com.hsmart.admin.application.exceptions.MarketplaceStatsUnavailableException;
import com.hsmart.admin.application.exceptions.DuplicatePendingReportException;
import com.hsmart.admin.application.exceptions.MissingUserContextException;
import com.hsmart.admin.application.exceptions.ProductModerationException;
import com.hsmart.admin.application.exceptions.ReportAlreadyProcessedException;
import com.hsmart.admin.application.exceptions.ReportNotFoundException;
import com.hsmart.admin.application.exceptions.UserStatusUpdateException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid request")
                .orElse("Invalid request");
        return ResponseEntity.badRequest().body(ApiResponse.error(HttpStatus.BAD_REQUEST, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error(HttpStatus.BAD_REQUEST, "Invalid request"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedJson(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error(HttpStatus.BAD_REQUEST, "Malformed request body"));
    }

    @ExceptionHandler(MarketplaceStatsUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleStatsUnavailable(MarketplaceStatsUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(HttpStatus.SERVICE_UNAVAILABLE, "Marketplace stats are temporarily unavailable"));
    }

    @ExceptionHandler(ProductModerationException.class)
    public ResponseEntity<ApiResponse<Void>> handleModerationFailure(ProductModerationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(HttpStatus.BAD_GATEWAY, "Product moderation update failed"));
    }

    @ExceptionHandler(UserStatusUpdateException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserStatusUpdateFailure(UserStatusUpdateException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(HttpStatus.BAD_GATEWAY, "User status update failed"));
    }

    @ExceptionHandler(MissingUserContextException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingUserContext(MissingUserContextException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(HttpStatus.UNAUTHORIZED, exception.getMessage()));
    }

    @ExceptionHandler(ReportNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleReportNotFound(ReportNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND, exception.getMessage()));
    }

    @ExceptionHandler(ReportAlreadyProcessedException.class)
    public ResponseEntity<ApiResponse<Void>> handleReportAlreadyProcessed(ReportAlreadyProcessedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(HttpStatus.CONFLICT, exception.getMessage()));
    }

    @ExceptionHandler(DuplicatePendingReportException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicatePendingReport(DuplicatePendingReportException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(HttpStatus.CONFLICT, exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected admin service error"));
    }
}
