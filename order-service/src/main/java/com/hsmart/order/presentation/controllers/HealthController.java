package com.hsmart.order.presentation.controllers;

import com.hsmart.order.application.dto.ApiResponse;
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
                "Order service is healthy",
                Map.of(
                        "service", "order-service",
                        "status", "healthy",
                        "port", 8085
                )
        ));
    }
}
