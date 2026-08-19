package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.PageResponseDTO;
import com.hsmart.backend.application.dto.UpdateProfileRequestDTO;
import com.hsmart.backend.application.dto.SellerTrustResponseDTO;
import com.hsmart.backend.application.dto.UserAddressResponseDTO;
import com.hsmart.backend.application.dto.UserAdminSummaryDTO;
import com.hsmart.backend.application.dto.UserProfileResponseDTO;
import com.hsmart.backend.application.dto.UserStatsResponseDTO;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
    UserProfileResponseDTO getProfile(String username);
    UserProfileResponseDTO updateProfile(String username, UpdateProfileRequestDTO request);
    UserProfileResponseDTO updateAvatar(String username, MultipartFile file);
    UserStatsResponseDTO getUserStats();
    SellerTrustResponseDTO getSellerTrustProfile(String sellerId);
    UserAddressResponseDTO getUserAddress(String userId);
    void updateUserActiveStatus(String userId, boolean active);
    PageResponseDTO<UserAdminSummaryDTO> listUsersForAdmin(String search, Boolean isActive, Pageable pageable);
    UserAdminSummaryDTO getUserForAdmin(Long userId);
}
