package com.hsmart.backend.presentation.controllers;

import com.hsmart.backend.application.dto.ApiResponse;
import com.hsmart.backend.application.dto.ChangeEmailRequestDTO;
import com.hsmart.backend.application.dto.ChangePasswordRequestDTO;
import com.hsmart.backend.application.dto.PageResponseDTO;
import com.hsmart.backend.application.dto.SellerTrustResponseDTO;
import com.hsmart.backend.application.dto.TokenRequestDTO;
import com.hsmart.backend.application.dto.UpdateProfileRequestDTO;
import com.hsmart.backend.application.dto.UserAddressResponseDTO;
import com.hsmart.backend.application.dto.UserAdminSummaryDTO;
import com.hsmart.backend.application.dto.UserProfileResponseDTO;
import com.hsmart.backend.application.dto.UserStatsResponseDTO;
import com.hsmart.backend.application.dto.UserStatusUpdateRequestDTO;
import com.hsmart.backend.infrastructure.exception.InvalidRequestException;
import com.hsmart.backend.service.AccountLifecycleService;
import com.hsmart.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final AccountLifecycleService accountLifecycleService;

    @GetMapping("/profile")
    @Operation(summary = "Get current authenticated user profile")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile fetched successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<UserProfileResponseDTO>> getProfile(Authentication authentication) {
        UserProfileResponseDTO response = userService.getProfile(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Profile fetched successfully", response));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update current authenticated user profile")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<UserProfileResponseDTO>> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequestDTO request
    ) {
        UserProfileResponseDTO response = userService.updateProfile(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Profile updated successfully", response));
    }

    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update current authenticated user avatar")
    public ResponseEntity<ApiResponse<UserProfileResponseDTO>> updateAvatar(
            Authentication authentication,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar
    ) {
        MultipartFile selectedFile = resolveAvatarFile(file, avatar);
        UserProfileResponseDTO response = userService.updateAvatar(authentication.getName(), selectedFile);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Avatar updated successfully", response));
    }

    @PutMapping("/password")
    @Operation(summary = "Change the current authenticated user's password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequestDTO request
    ) {
        accountLifecycleService.changePassword(
                authentication.getName(),
                request.currentPassword(),
                request.newPassword()
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Password changed successfully", null));
    }

    @PostMapping("/email/change")
    @Operation(summary = "Request an authenticated email change")
    public ResponseEntity<ApiResponse<Void>> requestEmailChange(
            Authentication authentication,
            @Valid @RequestBody ChangeEmailRequestDTO request
    ) {
        accountLifecycleService.requestEmailChange(authentication.getName(), request.newEmail());
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Email change verification has been sent to the new email address",
                null
        ));
    }

    @PostMapping("/email/confirm")
    @Operation(summary = "Confirm an authenticated email change with OTP")
    public ResponseEntity<ApiResponse<Void>> confirmEmailChange(
            Authentication authentication,
            @Valid @RequestBody TokenRequestDTO request
    ) {
        accountLifecycleService.confirmEmailChange(authentication.getName(), request.token());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Email changed successfully", null));
    }

    @GetMapping("/internal/stats")
    public ResponseEntity<ApiResponse<UserStatsResponseDTO>> getInternalStats() {
        UserStatsResponseDTO response = userService.getUserStats();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "User stats fetched successfully", response));
    }

    @GetMapping("/internal/{sellerId}/trust")
    public ResponseEntity<ApiResponse<SellerTrustResponseDTO>> getInternalSellerTrust(@PathVariable String sellerId) {
        SellerTrustResponseDTO response = userService.getSellerTrustProfile(sellerId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Seller trust profile fetched successfully", response));
    }

    @GetMapping("/internal/{userId}/address")
    public ResponseEntity<ApiResponse<UserAddressResponseDTO>> getInternalUserAddress(@PathVariable String userId) {
        UserAddressResponseDTO response = userService.getUserAddress(userId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "User address fetched successfully", response));
    }

    @PutMapping("/internal/{userId}/status")
    public ResponseEntity<ApiResponse<Void>> updateInternalUserStatus(
            @PathVariable String userId,
            @Valid @RequestBody UserStatusUpdateRequestDTO request
    ) {
        userService.updateUserActiveStatus(userId, request.getIsActive());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "User status updated successfully", null));
    }

    @GetMapping("/internal/admin/list")
    public ResponseEntity<ApiResponse<PageResponseDTO<UserAdminSummaryDTO>>> listUsersForAdmin(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            Pageable pageable
    ) {
        PageResponseDTO<UserAdminSummaryDTO> response = userService.listUsersForAdmin(search, isActive, pageable);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Users fetched successfully", response));
    }

    @GetMapping("/internal/admin/{userId}")
    public ResponseEntity<ApiResponse<UserAdminSummaryDTO>> getUserForAdmin(@PathVariable Long userId) {
        UserAdminSummaryDTO response = userService.getUserForAdmin(userId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "User fetched successfully", response));
    }

    private MultipartFile resolveAvatarFile(MultipartFile file, MultipartFile avatar) {
        if (file != null && !file.isEmpty()) {
            return file;
        }
        if (avatar != null && !avatar.isEmpty()) {
            return avatar;
        }
        throw new InvalidRequestException("Avatar image file is required");
    }
}
