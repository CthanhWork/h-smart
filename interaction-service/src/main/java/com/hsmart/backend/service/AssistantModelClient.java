package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.AssistantChatMessage;
import java.util.List;
import java.util.function.Consumer;

public interface AssistantModelClient {
    String generateReply(List<AssistantChatMessage> messages);

    default String generateDescriptionReply(List<AssistantChatMessage> messages) {
        return generateReply(messages);
    }

    default void generateStreamingReply(List<AssistantChatMessage> messages,
                                        Consumer<String> onToken, Runnable onComplete) {
        String reply = generateReply(messages);
        onToken.accept(reply);
        onComplete.run();
    }
}
