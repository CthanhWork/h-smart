package com.hsmart.backend.application.dto;

import com.hsmart.backend.domain.entities.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UserProfileResponse", description = "Thong tin ho so nguoi dung")
public class UserProfileResponseDTO {
    private Long id;
    private String username;
    private String email;
    private Role role;
    private String fullName;
    private String phoneNumber;
    private String provinceCode;
    private String province;
    private String districtCode;
    private String district;
    private String wardCode;
    private String ward;
    private String streetDetail;
    private String avatarUrl;
    private BigDecimal trustScore;
    private Long reviewCount;
    private boolean emailVerified;
}
