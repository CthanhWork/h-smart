package com.hsmart.order.application.dto;

public record UserAddressResponseDTO(
        String userId,
        String fullName,
        String phoneNumber,
        String province,
        String district,
        String ward,
        String streetDetail
) {
}
