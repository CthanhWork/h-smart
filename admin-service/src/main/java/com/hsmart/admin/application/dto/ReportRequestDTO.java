package com.hsmart.admin.application.dto;

import jakarta.validation.constraints.NotBlank;
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
public class ReportRequestDTO {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotBlank(message = "Report reason is required")
    @Size(max = 2000, message = "Report reason must not exceed 2000 characters")
    private String reason;
}
