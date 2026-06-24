package com.hsmart.backend.application.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hsmart.backend.application.dto.DetectionDTO;
import com.hsmart.backend.application.dto.PredictResponseDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductNamingSupportTest {

    private final ProductNamingSupport productNamingSupport = new ProductNamingSupport();

    @Test
    void shouldKeepRequestedTitleWhenPresent() {
        String resolvedTitle = productNamingSupport.resolveTitle("Used fridge", List.of());
        assertEquals("Used fridge", resolvedTitle);
    }

    @Test
    void shouldUseHighestConfidenceDetectionWhenTitleIsBlank() {
        List<DetectionDTO> detections = List.of(
                DetectionDTO.builder().label("chair").score(0.55).build(),
                DetectionDTO.builder().label("washing_machine").score(0.91).build()
        );

        String resolvedTitle = productNamingSupport.resolveTitle("   ", detections);
        assertEquals("Máy giặt", resolvedTitle);
    }

    @Test
    void shouldPreferTranslatedLabelFromAiServiceWhenPresent() {
        List<DetectionDTO> detections = List.of(
                DetectionDTO.builder().label("microwave").translatedLabel("Lò vi sóng").score(0.97).build(),
                DetectionDTO.builder().label("chair").score(0.22).build()
        );

        String resolvedTitle = productNamingSupport.resolveTitle(null, detections);
        assertEquals("Lò vi sóng", resolvedTitle);
    }

    @Test
    void shouldTranslateKnownModelLabelWhenNoTranslatedLabelIsProvided() {
        List<DetectionDTO> detections = List.of(
                DetectionDTO.builder().label("microwave").score(0.97).build()
        );

        String resolvedTitle = productNamingSupport.resolveTitle(null, detections);
        assertEquals("Lò vi sóng", resolvedTitle);
    }

    @Test
    void shouldNotFabricateNameWhenNoDetectionExists() {
        String resolvedTitle = productNamingSupport.resolveTitle(null, List.of());
        assertEquals("", resolvedTitle);
    }

    @Test
    void shouldFlagUnrecognizedWhenSentinelLabelAndNoDetections() {
        PredictResponseDTO unknown = PredictResponseDTO.builder()
                .label("unknown")
                .translatedLabel("Không xác định")
                .numDetections(0)
                .detections(List.of())
                .build();

        assertTrue(productNamingSupport.isUnrecognized(unknown));
    }

    @Test
    void shouldNotFlagUnrecognizedWhenDetectionExists() {
        PredictResponseDTO recognized = PredictResponseDTO.builder()
                .detections(List.of(DetectionDTO.builder().label("chair").score(0.8).build()))
                .build();

        assertFalse(productNamingSupport.isUnrecognized(recognized));
    }
}
