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
@Schema(name = "AuthResponse", description = "Ket qua xac thuc va token JWT")
public class AuthResponseDTO {
    private String accessToken;
    private String tokenType;
    private UserProfileResponseDTO user;
}
