package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.UpdateProfileRequestDTO;
import com.hsmart.backend.application.dto.SellerTrustResponseDTO;
import com.hsmart.backend.application.dto.UserProfileResponseDTO;
import com.hsmart.backend.application.dto.UserStatsResponseDTO;

public interface UserService {
    UserProfileResponseDTO getProfile(String username);
    UserProfileResponseDTO updateProfile(String username, UpdateProfileRequestDTO request);
    UserStatsResponseDTO getUserStats();
    SellerTrustResponseDTO getSellerTrustProfile(String sellerId);
}
