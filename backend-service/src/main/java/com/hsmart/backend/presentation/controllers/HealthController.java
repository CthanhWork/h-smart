package com.hsmart.backend.presentation.controllers;

import com.hsmart.backend.application.dto.ApiResponse;
import com.hsmart.backend.infrastructure.config.AiServiceProperties;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final AiServiceProperties aiServiceProperties;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> payload = Map.of(
                "status", "healthy",
                "service", "backend-service",
                "javaVersion", System.getProperty("java.version"),
                "aiServiceBaseUrl", aiServiceProperties.baseUrl()
        );

        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Backend is healthy", payload));
    }
}
