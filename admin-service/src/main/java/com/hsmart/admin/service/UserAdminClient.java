package com.hsmart.admin.service;

import com.hsmart.admin.application.dto.SellerTrustResponseDTO;

public interface UserAdminClient {
    SellerTrustResponseDTO getSellerTrustProfile(String sellerId);
    void updateUserActiveStatus(String userId, boolean active);
}
