package com.hsmart.admin.application.dto;

import com.hsmart.admin.domain.entities.ReportAction;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportActionRequestDTO {

    @NotNull(message = "Report action is required")
    private ReportAction action;
}
