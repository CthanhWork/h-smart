package com.hsmart.backend.application.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hsmart.backend.application.dto.DetectionDTO;
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
        assertEquals("Washing machine", resolvedTitle);
    }

    @Test
    void shouldTranslateKnownLvisLabelWhenTitleIsBlank() {
        List<DetectionDTO> detections = List.of(
                DetectionDTO.builder().label("microwave_oven").score(0.97).build(),
                DetectionDTO.builder().label("chair").score(0.22).build()
        );

        String resolvedTitle = productNamingSupport.resolveTitle(null, detections);
        assertEquals("Lo vi song", resolvedTitle);
    }

    @Test
    void shouldReturnFallbackWhenNoDetectionExists() {
        String resolvedTitle = productNamingSupport.resolveTitle(null, List.of());
        assertEquals("Unknown product", resolvedTitle);
    }
}
