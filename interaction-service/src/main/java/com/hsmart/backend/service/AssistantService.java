package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.ProductDescriptionRequest;
import com.hsmart.backend.application.dto.ProductDescriptionResponse;

public interface AssistantService {
    String chat(String userId, String message);

    ProductDescriptionResponse generateProductDescription(ProductDescriptionRequest request);
}
