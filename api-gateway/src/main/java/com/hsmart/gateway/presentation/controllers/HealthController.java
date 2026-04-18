package com.hsmart.gateway.presentation.controllers;

import com.hsmart.gateway.application.dto.ApiResponse;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        return ResponseEntity.ok(ApiResponse.success(
                200,
                "API Gateway is healthy",
                Map.of(
                        "service", "api-gateway",
                        "status", "healthy",
                        "port", 8000
                )
        ));
    }
}
