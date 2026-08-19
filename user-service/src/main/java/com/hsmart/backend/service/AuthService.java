package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.AuthResponseDTO;
import com.hsmart.backend.application.dto.LoginRequestDTO;
import com.hsmart.backend.application.dto.RefreshTokenRequestDTO;
import com.hsmart.backend.application.dto.RegisterRequestDTO;

public interface AuthService {
    AuthResponseDTO register(RegisterRequestDTO request);
    AuthResponseDTO login(LoginRequestDTO request);
    AuthResponseDTO refresh(RefreshTokenRequestDTO request);
    void logout(RefreshTokenRequestDTO request);
}
