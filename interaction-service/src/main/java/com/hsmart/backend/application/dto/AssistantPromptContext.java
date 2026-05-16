package com.hsmart.backend.application.dto;

import com.hsmart.backend.application.dto.IntentClassification.Intent;
import org.springframework.util.StringUtils;

public record AssistantPromptContext(
        Intent intent,
        String promptAddition,
        boolean realtimeUnavailable
) {

    public boolean hasPromptAddition() {
        return StringUtils.hasText(promptAddition);
    }
}
