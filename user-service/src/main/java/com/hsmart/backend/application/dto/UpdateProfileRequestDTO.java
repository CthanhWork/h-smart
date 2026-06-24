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

    @Schema(example = "79")
    private String provinceCode;

    @Schema(example = "760")
    private String districtCode;

    @Schema(example = "26734")
    private String wardCode;

    @Schema(example = "12 Nguyen Van Nghi Street")
    private String streetDetail;
}
