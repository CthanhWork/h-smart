package com.hsmart.backend.presentation.controllers;

import com.hsmart.backend.application.dto.ApiResponse;
import com.hsmart.backend.application.dto.ChatMessageResponseDTO;
import com.hsmart.backend.application.exceptions.MissingUserContextException;
import com.hsmart.backend.infrastructure.context.UserContextHolder;
import com.hsmart.backend.service.ChatService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/interactions/messages")
@RequiredArgsConstructor
public class ChatHistoryController {

    private final ChatService chatService;

    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ChatMessageResponseDTO>> uploadMediaMessage(
            @RequestParam String receiverId,
            @RequestParam @NotNull Long productId,
            @RequestParam(required = false) String content,
            @RequestParam("file") MultipartFile file
    ) {
        String currentUserId = requireCurrentUserId();
        ChatMessageResponseDTO response = chatService.processMediaMessage(
                currentUserId,
                receiverId,
                productId,
                content,
                file
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "Media message uploaded successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChatMessageResponseDTO>>> getConversation(
            @RequestParam String participantId,
            @RequestParam Long productId
    ) {
        String currentUserId = requireCurrentUserId();
        List<ChatMessageResponseDTO> conversation = chatService.getConversation(currentUserId, participantId, productId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Conversation fetched successfully", conversation));
    }

    private String requireCurrentUserId() {
        String currentUserId = UserContextHolder.getCurrentUserId();
        if (!StringUtils.hasText(currentUserId)) {
            throw new MissingUserContextException();
        }
        return currentUserId;
    }
}
