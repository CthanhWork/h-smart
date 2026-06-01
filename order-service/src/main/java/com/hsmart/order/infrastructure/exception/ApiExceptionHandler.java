package com.hsmart.order.infrastructure.exception;

import com.hsmart.order.application.dto.ApiResponse;
import com.hsmart.order.application.exceptions.MissingUserContextException;
import com.hsmart.order.application.exceptions.InvalidGhtkWebhookException;
import com.hsmart.order.application.exceptions.OrderNotFoundException;
import com.hsmart.order.application.exceptions.OrderStateException;
import com.hsmart.order.application.exceptions.ProductCatalogUnavailableException;
import com.hsmart.order.application.exceptions.ProductUnavailableException;
import com.hsmart.order.application.exceptions.ShippingProviderUnavailableException;
import com.hsmart.order.application.exceptions.ShippingAddressLookupUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MissingUserContextException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingUserContext(MissingUserContextException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(HttpStatus.UNAUTHORIZED, exception.getMessage()));
    }

    @ExceptionHandler(ShippingAddressLookupUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleShippingAddressLookupUnavailable(
            ShippingAddressLookupUnavailableException exception
    ) {
        log.warn("Shipping address lookup is unavailable while processing an order", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(HttpStatus.SERVICE_UNAVAILABLE, "Shipping address lookup is temporarily unavailable"));
    }

    @ExceptionHandler(InvalidGhtkWebhookException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidGhtkWebhook(InvalidGhtkWebhookException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(HttpStatus.UNAUTHORIZED, exception.getMessage()));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderNotFound(OrderNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND, exception.getMessage()));
    }

    @ExceptionHandler({ProductUnavailableException.class, OrderStateException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidOrderRequest(RuntimeException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }

    @ExceptionHandler(ProductCatalogUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleProductCatalogUnavailable(ProductCatalogUnavailableException exception) {
        log.warn("Product catalog is unavailable while processing an order", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(HttpStatus.SERVICE_UNAVAILABLE, "Product catalog is temporarily unavailable"));
    }

    @ExceptionHandler(ShippingProviderUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleShippingProviderUnavailable(
            ShippingProviderUnavailableException exception
    ) {
        log.warn("Shipping provider is unavailable while processing an order", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(HttpStatus.SERVICE_UNAVAILABLE, "Shipping provider is temporarily unavailable"));
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception exception) {
        log.error("Unexpected order-service error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"));
    }
}
