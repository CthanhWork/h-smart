package com.hsmart.backend.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LoginRequest", description = "Thong tin dang nhap")
public class LoginRequestDTO {

    @NotBlank
    @Schema(example = "nguyenvana")
    private String usernameOrEmail;

    @NotBlank
    @Schema(example = "123456")
    private String password;
}
