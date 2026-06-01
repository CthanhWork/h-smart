package com.hsmart.backend.service.impl;

import com.hsmart.backend.application.dto.SellerTrustResponseDTO;
import com.hsmart.backend.application.dto.UpdateProfileRequestDTO;
import com.hsmart.backend.application.dto.UserAddressResponseDTO;
import com.hsmart.backend.application.dto.UserProfileResponseDTO;
import com.hsmart.backend.application.dto.UserStatsResponseDTO;
import com.hsmart.backend.application.mapper.UserMapper;
import com.hsmart.backend.domain.entities.User;
import com.hsmart.backend.infrastructure.exception.ResourceNotFoundException;
import com.hsmart.backend.infrastructure.persistence.UserRepository;
import com.hsmart.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponseDTO getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return userMapper.toProfileResponse(user);
    }

    @Override
    public UserProfileResponseDTO updateProfile(String username, UpdateProfileRequestDTO request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        userMapper.updateProfile(request, user);
        return userMapper.toProfileResponse(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public UserStatsResponseDTO getUserStats() {
        return UserStatsResponseDTO.builder()
                .totalUsers(userRepository.count())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SellerTrustResponseDTO getSellerTrustProfile(String sellerId) {
        if (!StringUtils.hasText(sellerId)) {
            throw new ResourceNotFoundException("Seller not found");
        }

        User seller = userRepository.findByUsername(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));

        return SellerTrustResponseDTO.builder()
                .sellerId(seller.getUsername())
                .trustScore(seller.getTrustScore())
                .reviewCount(seller.getReviewCount())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserAddressResponseDTO getUserAddress(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new ResourceNotFoundException("User not found");
        }

        User user = userRepository.findByUsername(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return UserAddressResponseDTO.builder()
                .userId(user.getUsername())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .province(user.getProvince())
                .district(user.getDistrict())
                .ward(user.getWard())
                .streetDetail(user.getStreetDetail())
                .build();
    }

    @Override
    public void updateUserActiveStatus(String userId, boolean active) {
        if (!StringUtils.hasText(userId)) {
            throw new ResourceNotFoundException("User not found");
        }

        User user = userRepository.findByUsername(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setActive(active);
        userRepository.save(user);
    }
}
