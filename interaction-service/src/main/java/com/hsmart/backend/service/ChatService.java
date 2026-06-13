package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.ChatMessageRequestDTO;
import com.hsmart.backend.application.dto.ChatMessageResponseDTO;
import com.hsmart.backend.application.dto.ChatConversationSummaryDTO;
import java.util.List;

public interface ChatService {
    ChatMessageResponseDTO processIncomingMessage(String senderId, ChatMessageRequestDTO request);
    List<ChatMessageResponseDTO> getConversation(String currentUserId, String participantId, Long productId);
    List<ChatConversationSummaryDTO> getConversationSummaries(String currentUserId);
}
