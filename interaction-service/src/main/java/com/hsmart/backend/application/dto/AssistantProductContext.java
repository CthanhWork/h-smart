package com.hsmart.backend.application.dto;

import java.util.List;

public record AssistantProductContext(
        List<String> keywords,
        String promptAddition,
        boolean realtimeUnavailable,
        int productCount
) {
    public boolean hasPromptAddition() {
        return promptAddition != null && !promptAddition.isBlank();
    }
}
