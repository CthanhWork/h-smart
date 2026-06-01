package com.hsmart.admin.service;

import com.hsmart.admin.application.dto.ManualProductModerationRequestDTO;

public interface AdminManagementService {
    void banUser(String userId);
    void moderateProduct(Long productId, ManualProductModerationRequestDTO request);
}
