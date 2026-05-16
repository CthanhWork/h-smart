package com.hsmart.backend.infrastructure.exception;

import com.hsmart.backend.application.dto.ApiResponse;
import com.hsmart.backend.application.exceptions.AssistantGatewayTimeoutException;
import com.hsmart.backend.application.exceptions.AssistantServiceUnavailableException;
import com.hsmart.backend.application.exceptions.InvalidInteractionRequestException;
import com.hsmart.backend.application.exceptions.MissingUserContextException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MissingUserContextException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingUserContext(MissingUserContextException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(HttpStatus.UNAUTHORIZED, exception.getMessage()));
    }

    @ExceptionHandler(InvalidInteractionRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(InvalidInteractionRequestException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }

    @ExceptionHandler(AssistantServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleAssistantUnavailable(AssistantServiceUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage()));
    }

    @ExceptionHandler(AssistantGatewayTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleAssistantGatewayTimeout(AssistantGatewayTimeoutException exception) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ApiResponse.error(HttpStatus.GATEWAY_TIMEOUT, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");

        return ResponseEntity.badRequest().body(ApiResponse.error(HttpStatus.BAD_REQUEST, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedJson(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error(HttpStatus.BAD_REQUEST, "Malformed request body"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestParameter(MissingServletRequestParameterException exception) {
        String message = exception.getParameterName() + " is required";
        return ResponseEntity.badRequest().body(ApiResponse.error(HttpStatus.BAD_REQUEST, message));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(ApiResponse.error(HttpStatus.valueOf(exception.getStatusCode().value()), exception.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"));
    }
}
