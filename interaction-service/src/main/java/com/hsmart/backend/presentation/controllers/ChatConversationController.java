package com.hsmart.backend.presentation.controllers;

import com.hsmart.backend.application.dto.ApiResponse;
import com.hsmart.backend.application.dto.ChatConversationSummaryDTO;
import com.hsmart.backend.application.exceptions.MissingUserContextException;
import com.hsmart.backend.infrastructure.context.UserContextHolder;
import com.hsmart.backend.service.ChatService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interactions/conversations")
@RequiredArgsConstructor
public class ChatConversationController {

    private final ChatService chatService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChatConversationSummaryDTO>>> getConversationSummaries() {
        String currentUserId = UserContextHolder.getCurrentUserId();
        if (!StringUtils.hasText(currentUserId)) {
            throw new MissingUserContextException();
        }

        List<ChatConversationSummaryDTO> conversations = chatService.getConversationSummaries(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Conversation summaries fetched successfully",
                conversations
        ));
    }
}
