package com.hsmart.backend.presentation.controllers;

import com.hsmart.backend.application.dto.ApiResponse;
import com.hsmart.backend.application.dto.AssistantChatRequestDTO;
import com.hsmart.backend.application.dto.ChatMessageResponseDTO;
import com.hsmart.backend.application.dto.ProductDescriptionRequest;
import com.hsmart.backend.application.dto.ProductDescriptionResponse;
import com.hsmart.backend.application.exceptions.MissingUserContextException;
import com.hsmart.backend.infrastructure.context.UserContextHolder;
import com.hsmart.backend.service.AssistantService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestParam String message) {
        String currentUserId = requireCurrentUserId();
        return assistantService.streamChat(currentUserId, message);
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ChatMessageResponseDTO>>> getHistory(
            @RequestParam(defaultValue = "30") int limit
    ) {
        List<ChatMessageResponseDTO> history = assistantService.getHistory(requireCurrentUserId(), limit);
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Assistant history retrieved successfully",
                history
        ));
    }

    @DeleteMapping("/history")
    public ResponseEntity<ApiResponse<Void>> clearHistory() {
        assistantService.clearHistory(requireCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Assistant history cleared successfully",
                null
        ));
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
