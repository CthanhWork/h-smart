package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.ChatMessageResponseDTO;
import com.hsmart.backend.application.dto.ProductDescriptionRequest;
import com.hsmart.backend.application.dto.ProductDescriptionResponse;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AssistantService {
    String chat(String userId, String message);

    SseEmitter streamChat(String userId, String message);

    List<ChatMessageResponseDTO> getHistory(String userId, int limit);

    void clearHistory(String userId);

    ProductDescriptionResponse generateProductDescription(ProductDescriptionRequest request);
}
