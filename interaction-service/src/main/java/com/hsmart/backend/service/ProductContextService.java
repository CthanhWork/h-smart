package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.AssistantProductContext;

public interface ProductContextService {
    AssistantProductContext buildContext(String message, String userId, String traceId);
}
