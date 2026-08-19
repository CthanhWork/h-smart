package com.hsmart.backend.application.dto;

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
public class AssistantChatRequestDTO {

    @NotBlank
    @Size(max = 2000, message = "message must not exceed 2000 characters")
    private String message;
}
