package com.hsmart.backend.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RegisterRequest", description = "Thong tin dang ky tai khoan moi")
public class RegisterRequestDTO {

    @NotBlank
    @Size(min = 4, max = 100)
    @Schema(example = "nguyenvana")
    private String username;

    @NotBlank
    @Email
    @Schema(example = "vana@example.com")
    private String email;

    @NotBlank
    @Size(min = 8, max = 100)
    @Schema(example = "StrongPassword123!")
    private String password;

    @Schema(example = "Nguyen Van A")
    private String fullName;

    @Schema(example = "0901234567")
    private String phoneNumber;

    @Schema(example = "Ho Chi Minh City")
    private String province;

    @Schema(example = "Thu Duc City")
    private String district;

    @Schema(example = "Linh Trung Ward")
    private String ward;

    @Schema(example = "1 Vo Van Ngan Street")
    private String streetDetail;

    @Schema(example = "https://example.com/avatar.jpg")
    private String avatarUrl;
}
