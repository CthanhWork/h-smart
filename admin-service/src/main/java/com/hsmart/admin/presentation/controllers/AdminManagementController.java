package com.hsmart.admin.presentation.controllers;

import com.hsmart.admin.application.dto.ApiResponse;
import com.hsmart.admin.application.dto.ManualProductModerationRequestDTO;
import com.hsmart.admin.service.AdminManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminManagementController {

    private final AdminManagementService adminManagementService;

    @PostMapping("/users/{userId}/ban")
    public ResponseEntity<ApiResponse<Void>> banUser(@PathVariable String userId) {
        adminManagementService.banUser(userId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "User banned successfully", null));
    }

    @PostMapping("/products/{productId}/moderate")
    public ResponseEntity<ApiResponse<Void>> moderateProduct(
            @PathVariable Long productId,
            @Valid @RequestBody ManualProductModerationRequestDTO request
    ) {
        adminManagementService.moderateProduct(productId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Product moderated successfully", null));
    }
}
