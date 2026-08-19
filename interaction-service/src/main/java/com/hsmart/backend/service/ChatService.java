package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.ChatMessageRequestDTO;
import com.hsmart.backend.application.dto.ChatMessageResponseDTO;
import com.hsmart.backend.application.dto.ChatConversationSummaryDTO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ChatService {
    ChatMessageResponseDTO processIncomingMessage(String senderId, ChatMessageRequestDTO request);
    ChatMessageResponseDTO processMediaMessage(String senderId, String receiverId, Long productId, String content, MultipartFile file);
    List<ChatMessageResponseDTO> getConversation(String currentUserId, String participantId, Long productId);
    List<ChatConversationSummaryDTO> getConversationSummaries(String currentUserId);
}
