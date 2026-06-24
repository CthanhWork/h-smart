package com.hsmart.backend.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class DetectionDTO {
    private String label;

    @JsonProperty("class_id")
    private Integer classId;

    private Double score;
    private List<Double> bbox;

    @JsonProperty("translated_label")
    private String translatedLabel;
}
