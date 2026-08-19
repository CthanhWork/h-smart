package com.hsmart.admin.service;

import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.application.dto.SellerTrustResponseDTO;
import com.hsmart.admin.application.dto.UserAdminSummaryDTO;
import org.springframework.data.domain.Pageable;

public interface UserAdminClient {
    SellerTrustResponseDTO getSellerTrustProfile(String sellerId);
    void updateUserActiveStatus(String userId, boolean active);
    PageResponseDTO<UserAdminSummaryDTO> listUsers(String search, Boolean isActive, Pageable pageable);
    UserAdminSummaryDTO getUserById(Long userId);
}
