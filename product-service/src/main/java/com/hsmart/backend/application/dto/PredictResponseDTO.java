package com.hsmart.backend.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PredictResponseDTO {
    private String label;

    private Double confidence;

    @JsonProperty("translated_label")
    private String translatedLabel;

    @JsonProperty("num_detections")
    private Integer numDetections;

    @Builder.Default
    private List<DetectionDTO> detections = new ArrayList<>();
}
