package com.hsmart.admin.service;

import com.hsmart.admin.application.dto.ProductCreatedEvent;

public interface ProductModerationService {
    void moderateProduct(ProductCreatedEvent event);
}
