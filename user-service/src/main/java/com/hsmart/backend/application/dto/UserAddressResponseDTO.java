package com.hsmart.backend.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAddressResponseDTO {
    private String userId;
    private String fullName;
    private String phoneNumber;
    private String provinceCode;
    private String province;
    private String district;
    private String ward;
    private String streetDetail;
}
