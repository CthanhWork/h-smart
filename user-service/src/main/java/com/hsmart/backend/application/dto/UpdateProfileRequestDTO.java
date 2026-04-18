package com.hsmart.backend.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateProfileRequest", description = "Thong tin cap nhat ho so nguoi dung")
public class UpdateProfileRequestDTO {
    @Schema(example = "Nguyen Van A")
    private String fullName;

    @Schema(example = "0901234567")
    private String phoneNumber;

    @Schema(example = "Go Vap, Ho Chi Minh City")
    private String address;

    @Schema(example = "https://example.com/avatar-new.jpg")
    private String avatarUrl;
}
