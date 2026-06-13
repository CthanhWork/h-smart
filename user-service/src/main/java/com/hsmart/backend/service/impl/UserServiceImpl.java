package com.hsmart.backend.service.impl;

import com.hsmart.backend.application.dto.SellerTrustResponseDTO;
import com.hsmart.backend.application.dto.UpdateProfileRequestDTO;
import com.hsmart.backend.application.dto.UserAddressResponseDTO;
import com.hsmart.backend.application.dto.UserProfileResponseDTO;
import com.hsmart.backend.application.dto.ResolvedLocationDTO;
import com.hsmart.backend.application.dto.UserStatsResponseDTO;
import com.hsmart.backend.application.mapper.UserMapper;
import com.hsmart.backend.domain.entities.User;
import com.hsmart.backend.infrastructure.exception.InvalidLocationException;
import com.hsmart.backend.infrastructure.exception.ResourceNotFoundException;
import com.hsmart.backend.infrastructure.persistence.UserRepository;
import com.hsmart.backend.service.LocationCatalogService;
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
    private final LocationCatalogService locationCatalogService;

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
        applyResolvedAddress(user, request.getProvinceCode(), request.getDistrictCode(), request.getWardCode(), request.getStreetDetail());
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

    private void applyResolvedAddress(
            User user,
            String provinceCode,
            String districtCode,
            String wardCode,
            String streetDetail
    ) {
        String normalizedStreetDetail = normalizeOptionalText(streetDetail);
        boolean hasAnyAddressInput = StringUtils.hasText(provinceCode)
                || StringUtils.hasText(districtCode)
                || StringUtils.hasText(wardCode)
                || StringUtils.hasText(normalizedStreetDetail);

        if (!hasAnyAddressInput) {
            user.setProvinceCode(null);
            user.setProvince(null);
            user.setDistrictCode(null);
            user.setDistrict(null);
            user.setWardCode(null);
            user.setWard(null);
            user.setStreetDetail(null);
            return;
        }

        if (!StringUtils.hasText(provinceCode)
                && !StringUtils.hasText(districtCode)
                && !StringUtils.hasText(wardCode)
                && isLegacyAddressPresent(user)) {
            user.setStreetDetail(normalizedStreetDetail);
            return;
        }

        if (!StringUtils.hasText(normalizedStreetDetail)) {
            throw new InvalidLocationException("Street detail is required when an address is provided");
        }

        ResolvedLocationDTO resolvedLocation = locationCatalogService.resolveLocation(provinceCode, districtCode, wardCode);
        user.setProvinceCode(resolvedLocation.provinceCode());
        user.setProvince(resolvedLocation.provinceName());
        user.setDistrictCode(resolvedLocation.districtCode());
        user.setDistrict(resolvedLocation.districtName());
        user.setWardCode(resolvedLocation.wardCode());
        user.setWard(resolvedLocation.wardName());
        user.setStreetDetail(normalizedStreetDetail);
    }

    private String normalizeOptionalText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean isLegacyAddressPresent(User user) {
        return !StringUtils.hasText(user.getProvinceCode())
                && !StringUtils.hasText(user.getDistrictCode())
                && !StringUtils.hasText(user.getWardCode())
                && StringUtils.hasText(user.getProvince())
                && StringUtils.hasText(user.getDistrict())
                && StringUtils.hasText(user.getWard());
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
