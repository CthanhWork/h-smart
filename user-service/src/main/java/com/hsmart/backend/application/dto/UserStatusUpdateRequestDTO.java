package com.hsmart.backend.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusUpdateRequestDTO {

    @NotNull(message = "User active status is required")
    @JsonProperty("isActive")
    private Boolean isActive;
}
