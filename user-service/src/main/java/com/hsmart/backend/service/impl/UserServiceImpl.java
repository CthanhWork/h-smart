package com.hsmart.backend.service.impl;

import com.hsmart.backend.application.dto.PageResponseDTO;
import com.hsmart.backend.application.dto.SellerTrustResponseDTO;
import com.hsmart.backend.application.dto.UpdateProfileRequestDTO;
import com.hsmart.backend.application.dto.UserAddressResponseDTO;
import com.hsmart.backend.application.dto.UserAdminSummaryDTO;
import com.hsmart.backend.application.dto.UserProfileResponseDTO;
import com.hsmart.backend.application.dto.ResolvedLocationDTO;
import com.hsmart.backend.application.dto.UserStatsResponseDTO;
import com.hsmart.backend.application.mapper.UserMapper;
import com.hsmart.backend.domain.entities.User;
import com.hsmart.backend.infrastructure.config.ApplicationProperties;
import com.hsmart.backend.infrastructure.config.StorageProperties;
import com.hsmart.backend.infrastructure.exception.FileStorageException;
import com.hsmart.backend.infrastructure.exception.InvalidLocationException;
import com.hsmart.backend.infrastructure.exception.InvalidRequestException;
import com.hsmart.backend.infrastructure.exception.ResourceNotFoundException;
import com.hsmart.backend.infrastructure.persistence.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import com.hsmart.backend.service.LocationCatalogService;
import com.hsmart.backend.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final LocationCatalogService locationCatalogService;
    private final StorageProperties storageProperties;
    private final ApplicationProperties applicationProperties;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponseDTO getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toProfileResponse(user);
    }

    @Override
    public UserProfileResponseDTO updateProfile(String username, UpdateProfileRequestDTO request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        userMapper.updateProfile(request, user);
        applyResolvedAddress(user, request.getProvinceCode(), request.getDistrictCode(), request.getWardCode(), request.getStreetDetail());
        return toProfileResponse(userRepository.save(user));
    }

    @Override
    public UserProfileResponseDTO updateAvatar(String username, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Avatar image file is required");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String previousAvatarUrl = user.getAvatarUrl();
        user.setAvatarUrl(storeAvatarFile(file));
        User savedUser = userRepository.save(user);
        deleteOwnedAvatarIfPresent(previousAvatarUrl);
        return toProfileResponse(savedUser);
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

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<UserAdminSummaryDTO> listUsersForAdmin(String search, Boolean isActive, Pageable pageable) {
        String normalizedSearch = StringUtils.hasText(search) ? search.trim() : null;
        Page<User> users;

        if (normalizedSearch == null) {
            users = isActive == null
                    ? userRepository.findAll(pageable)
                    : userRepository.findByActive(isActive, pageable);
        } else {
            users = isActive == null
                    ? userRepository.searchForAdmin(normalizedSearch, pageable)
                    : userRepository.searchForAdminByActive(normalizedSearch, isActive, pageable);
        }

        return PageResponseDTO.from(users.map(this::toAdminSummary));
    }

    @Override
    @Transactional(readOnly = true)
    public UserAdminSummaryDTO getUserForAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toAdminSummary(user);
    }

    private UserAdminSummaryDTO toAdminSummary(User user) {
        return UserAdminSummaryDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .active(user.isActive())
                .emailVerified(user.isEmailVerified())
                .trustScore(user.getTrustScore())
                .reviewCount(user.getReviewCount())
                .province(user.getProvince())
                .district(user.getDistrict())
                .build();
    }

    private UserProfileResponseDTO toProfileResponse(User user) {
        UserProfileResponseDTO response = userMapper.toProfileResponse(user);
        response.setAvatarUrl(toAbsoluteAvatarUrl(response.getAvatarUrl()));
        return response;
    }

    private String storeAvatarFile(MultipartFile file) {
        String originalFilename = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename().trim()
                : "avatar.jpg";

        String extension = "";
        int lastDotIndex = originalFilename.lastIndexOf('.');
        if (lastDotIndex >= 0) {
            extension = originalFilename.substring(lastDotIndex);
        }

        String storedFilename = UUID.randomUUID() + extension;
        Path uploadDir = Paths.get(storageProperties.uploadDir()).toAbsolutePath().normalize();
        Path targetPath = uploadDir.resolve(storedFilename);

        try {
            Files.createDirectories(uploadDir);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new FileStorageException("Unable to store uploaded avatar image", exception);
        }

        return "api/v1/users/media/" + storedFilename;
    }

    private void deleteOwnedAvatarIfPresent(String avatarUrl) {
        String relativeAvatarPath = extractOwnedAvatarPath(avatarUrl);
        if (relativeAvatarPath == null) {
            return;
        }

        Path uploadDir = Paths.get(storageProperties.uploadDir()).toAbsolutePath().normalize();
        Path avatarPath = uploadDir.resolve(relativeAvatarPath).normalize();
        if (!avatarPath.startsWith(uploadDir)) {
            return;
        }

        try {
            Files.deleteIfExists(avatarPath);
        } catch (IOException exception) {
            throw new FileStorageException("Unable to replace previous avatar image", exception);
        }
    }

    private String extractOwnedAvatarPath(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl)) {
            return null;
        }

        String normalizedAvatarUrl = avatarUrl.trim();
        String relativePrefix = "api/v1/users/media/";
        if (normalizedAvatarUrl.startsWith(relativePrefix)) {
            return normalizedAvatarUrl.substring(relativePrefix.length());
        }

        String publicBaseUrl = normalizeBaseUrl(applicationProperties.publicBaseUrl());
        String absolutePrefix = publicBaseUrl + "/" + relativePrefix;
        if (normalizedAvatarUrl.startsWith(absolutePrefix)) {
            return normalizedAvatarUrl.substring(absolutePrefix.length());
        }

        return null;
    }

    private String toAbsoluteAvatarUrl(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl)) {
            return avatarUrl;
        }

        String normalizedAvatarUrl = avatarUrl.trim();
        if (normalizedAvatarUrl.startsWith("http://") || normalizedAvatarUrl.startsWith("https://")) {
            return normalizedAvatarUrl;
        }

        String publicBaseUrl = normalizeBaseUrl(applicationProperties.publicBaseUrl());
        if (publicBaseUrl.isEmpty()) {
            return normalizedAvatarUrl;
        }

        String normalizedPath = normalizedAvatarUrl.startsWith("/") ? normalizedAvatarUrl : "/" + normalizedAvatarUrl;
        return publicBaseUrl + normalizedPath;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
