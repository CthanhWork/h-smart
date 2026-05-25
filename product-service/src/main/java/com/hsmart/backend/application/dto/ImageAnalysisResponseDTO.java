package com.hsmart.backend.application.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageAnalysisResponseDTO {
    private String suggestedName;
    private BigDecimal suggestedPrice;
    private PredictResponseDTO aiMetadata;
}
