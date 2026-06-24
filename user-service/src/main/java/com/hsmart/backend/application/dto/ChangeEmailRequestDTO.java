package com.hsmart.backend.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ChangeEmailRequestDTO(
        @NotBlank(message = "New email is required")
        @Email(message = "New email must be a valid email address")
        String newEmail
) {
}
