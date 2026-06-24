package com.hsmart.payment.presentation.controllers;

import com.hsmart.payment.application.dto.ApiResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Payment service is healthy",
                Map.of(
                        "service", "payment-service",
                        "status", "healthy",
                        "port", 8087
                )
        ));
    }
}
