package com.hsmart.backend.presentation.controllers;

import com.hsmart.backend.application.dto.ApiResponse;
import com.hsmart.backend.application.dto.AssistantChatRequestDTO;
import com.hsmart.backend.application.dto.ProductDescriptionRequest;
import com.hsmart.backend.application.dto.ProductDescriptionResponse;
import com.hsmart.backend.application.exceptions.MissingUserContextException;
import com.hsmart.backend.infrastructure.context.UserContextHolder;
import com.hsmart.backend.service.AssistantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<String>> chat(@Valid @RequestBody AssistantChatRequestDTO request) {
        String currentUserId = requireCurrentUserId();
        String response = assistantService.chat(currentUserId, request.getMessage());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Assistant response generated successfully", response));
    }

    @PostMapping("/generate-description")
    public ResponseEntity<ApiResponse<ProductDescriptionResponse>> generateDescription(
            @Valid @RequestBody ProductDescriptionRequest request
    ) {
        requireCurrentUserId();
        ProductDescriptionResponse response = assistantService.generateProductDescription(request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Description generated successfully", response));
    }

    private String requireCurrentUserId() {
        String currentUserId = UserContextHolder.getCurrentUserId();
        if (!StringUtils.hasText(currentUserId)) {
            throw new MissingUserContextException();
        }
        return currentUserId;
    }
}
