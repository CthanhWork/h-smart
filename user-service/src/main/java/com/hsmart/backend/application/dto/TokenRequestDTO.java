package com.hsmart.backend.application.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRequestDTO(
        @NotBlank String token
) {
}
