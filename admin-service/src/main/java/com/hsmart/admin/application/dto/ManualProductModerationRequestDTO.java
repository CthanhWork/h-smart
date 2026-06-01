package com.hsmart.admin.application.dto;

import com.hsmart.admin.domain.entities.ManualProductModerationAction;
import jakarta.validation.constraints.NotNull;
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
}
