package com.hsmart.admin.application.dto;

import com.hsmart.admin.domain.entities.ManualProductModerationAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualProductModerationRequestDTO {

    @NotNull(message = "Moderation action is required")
    private ManualProductModerationAction action;

    @Size(max = 1000, message = "Moderation reason must not exceed 1000 characters")
    private String reason;
}
