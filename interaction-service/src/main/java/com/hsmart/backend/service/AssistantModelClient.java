package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.AssistantChatMessage;
import java.util.List;

public interface AssistantModelClient {
    String generateReply(List<AssistantChatMessage> messages);

    default String generateDescriptionReply(List<AssistantChatMessage> messages) {
        return generateReply(messages);
    }
}
