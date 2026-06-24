package com.hsmart.backend.application.mapper;

import com.hsmart.backend.application.dto.DetectionDTO;
import com.hsmart.backend.application.dto.PredictResponseDTO;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProductNamingSupport {

    /** Sentinel label returned by ai-service when no object is recognized. */
    public static final String UNKNOWN_LABEL = "unknown";

    // Keys mirror the 20 classes emitted by ai-service (yolo_detector.py). Used only as a fallback
    // when a detection does not carry a translated_label from ai-service.
    private static final Map<String, String> LABEL_TRANSLATIONS = Map.ofEntries(
            Map.entry("bed", "Giường"),
            Map.entry("cabinet", "Tủ"),
            Map.entry("chair", "Ghế"),
            Map.entry("table", "Bàn"),
            Map.entry("desk", "Bàn làm việc"),
            Map.entry("sofa", "Sofa"),
            Map.entry("blender", "Máy xay sinh tố"),
            Map.entry("dishwasher", "Máy rửa chén"),
            Map.entry("fan", "Quạt"),
            Map.entry("kettle", "Ấm đun nước"),
            Map.entry("lamp", "Đèn"),
            Map.entry("microwave", "Lò vi sóng"),
            Map.entry("mirror", "Gương"),
            Map.entry("oven_stove", "Lò nướng/Bếp"),
            Map.entry("refrigerator", "Tủ lạnh"),
            Map.entry("sink", "Bồn rửa"),
            Map.entry("faucet", "Vòi nước"),
            Map.entry("television", "Tivi"),
            Map.entry("toaster", "Máy nướng bánh mì"),
            Map.entry("washing_machine", "Máy giặt")
    );

    /**
     * True when ai-service did not confidently recognize any object in the image
     * (empty detections or the {@code unknown} sentinel). Callers must not fabricate a name in this case.
     */
    public boolean isUnrecognized(PredictResponseDTO aiMetadata) {
        if (aiMetadata == null) {
            return true;
        }
        boolean hasDetection = aiMetadata.getDetections() != null && aiMetadata.getDetections().stream()
                .anyMatch(detection -> StringUtils.hasText(detection.getLabel()));
        boolean hasUsableLabel = StringUtils.hasText(aiMetadata.getLabel())
                && !UNKNOWN_LABEL.equalsIgnoreCase(aiMetadata.getLabel().trim());
        return !hasDetection && !hasUsableLabel;
    }

    public String resolveTitle(String requestedTitle, List<DetectionDTO> detections) {
        if (StringUtils.hasText(requestedTitle)) {
            return requestedTitle.trim();
        }
        if (detections == null) {
            return "";
        }

        return detections.stream()
                .filter(detection -> StringUtils.hasText(detection.getLabel()))
                .max((left, right) -> Double.compare(
                        left.getScore() != null ? left.getScore() : 0.0,
                        right.getScore() != null ? right.getScore() : 0.0
                ))
                .map(this::nameOfDetection)
                .orElse("");
    }

    public String resolveSuggestedName(String label) {
        if (!StringUtils.hasText(label) || UNKNOWN_LABEL.equalsIgnoreCase(label.trim())) {
            return "";
        }
        return humanizeLabel(label);
    }

    private String nameOfDetection(DetectionDTO detection) {
        if (StringUtils.hasText(detection.getTranslatedLabel())) {
            return detection.getTranslatedLabel().trim();
        }
        return humanizeLabel(detection.getLabel());
    }

    private String humanizeLabel(String label) {
        String normalizedKey = label.toLowerCase(Locale.ROOT).trim();
        String translated = LABEL_TRANSLATIONS.get(normalizedKey);
        if (StringUtils.hasText(translated)) {
            return translated;
        }

        String normalized = normalizedKey.replace('_', ' ').replace('-', ' ').trim();
        if (normalized.isBlank()) {
            return "";
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
