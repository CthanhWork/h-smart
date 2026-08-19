package com.hsmart.backend.application.dto;

public record UserAddressResponseDTO(
        String userId,
        String fullName,
        String phoneNumber,
        String provinceCode,
        String province,
        String district,
        String ward,
        String streetDetail
) {
}
