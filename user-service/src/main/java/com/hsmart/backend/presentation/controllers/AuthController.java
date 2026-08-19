package com.hsmart.backend.presentation.controllers;

import com.hsmart.backend.application.dto.ApiResponse;
import com.hsmart.backend.application.dto.AuthResponseDTO;
import com.hsmart.backend.application.dto.LoginRequestDTO;
import com.hsmart.backend.application.dto.RefreshTokenRequestDTO;
import com.hsmart.backend.application.dto.RegisterRequestDTO;
import com.hsmart.backend.application.dto.EmailRequestDTO;
import com.hsmart.backend.application.dto.ResetPasswordRequestDTO;
import com.hsmart.backend.application.dto.TokenRequestDTO;
import com.hsmart.backend.service.AccountLifecycleService;
import com.hsmart.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AccountLifecycleService accountLifecycleService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User registered successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Username or email already exists")
    })
    public ResponseEntity<ApiResponse<AuthResponseDTO>> register(@Valid @RequestBody RegisterRequestDTO request) {
        AuthResponseDTO response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        HttpStatus.CREATED,
                        "User registered successfully. Check your email to verify the account.",
                        response
                ));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with username/email and password")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<ApiResponse<AuthResponseDTO>> login(@Valid @RequestBody LoginRequestDTO request) {
        AuthResponseDTO response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Login successful", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponseDTO>> refresh(@Valid @RequestBody RefreshTokenRequestDTO request) {
        AuthResponseDTO response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Token refreshed successfully", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequestDTO request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Logout successful", null));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody TokenRequestDTO request) {
        accountLifecycleService.verifyEmail(request.token());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Email verified successfully", null));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@Valid @RequestBody EmailRequestDTO request) {
        accountLifecycleService.sendVerificationEmail(request.email());
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "If the account exists and is not verified, a verification email has been sent.",
                null
        ));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody EmailRequestDTO request) {
        accountLifecycleService.requestPasswordReset(request.email());
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "If the account exists, a password reset email has been sent.",
                null
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO request) {
        accountLifecycleService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Password reset successfully", null));
    }
}
