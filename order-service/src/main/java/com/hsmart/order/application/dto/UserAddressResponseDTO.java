package com.hsmart.order.application.dto;

public record UserAddressResponseDTO(
        String userId,
        String fullName,
        String phoneNumber,
        String provinceCode,
        String province,
        String districtCode,
        String district,
        String wardCode,
        String ward,
        String streetDetail
) {
}
