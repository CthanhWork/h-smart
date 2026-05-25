package com.hsmart.backend.service.impl;

import com.hsmart.backend.application.dto.DetectionDTO;
import com.hsmart.backend.application.dto.ImageAnalysisResponseDTO;
import com.hsmart.backend.application.dto.PredictResponseDTO;
import com.hsmart.backend.application.mapper.ProductNamingSupport;
import com.hsmart.backend.service.PriceSuggestionService;
import com.hsmart.backend.service.ProductImageAnalysisService;
import com.hsmart.backend.service.VisionService;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageAnalysisServiceImpl implements ProductImageAnalysisService {

    private final VisionService visionService;
    private final PriceSuggestionService priceSuggestionService;
    private final ProductNamingSupport productNamingSupport;

    @Override
    public ImageAnalysisResponseDTO analyzeImage(MultipartFile file) {
        PredictResponseDTO aiMetadata = visionService.detectObjects(file);
        String label = resolveLabel(aiMetadata);
        BigDecimal suggestedPrice = priceSuggestionService.findSuggestedPrice(label).orElse(null);
        String suggestedName = resolveSuggestedName(aiMetadata, label);

        log.info("Analyzed product image with AI label {} and suggested price {}", label, suggestedPrice);
        return ImageAnalysisResponseDTO.builder()
                .suggestedName(suggestedName)
                .suggestedPrice(suggestedPrice)
                .aiMetadata(aiMetadata)
                .build();
    }

    private String resolveSuggestedName(PredictResponseDTO aiMetadata, String label) {
        if (StringUtils.hasText(aiMetadata.getTranslatedLabel())) {
            return aiMetadata.getTranslatedLabel().trim();
        }
        return productNamingSupport.resolveSuggestedName(label);
    }

    private String resolveLabel(PredictResponseDTO aiMetadata) {
        if (StringUtils.hasText(aiMetadata.getLabel())) {
            return aiMetadata.getLabel().trim();
        }

        List<DetectionDTO> detections = aiMetadata.getDetections() != null
                ? aiMetadata.getDetections()
                : Collections.emptyList();

        return detections.stream()
                .filter(detection -> StringUtils.hasText(detection.getLabel()))
                .max(Comparator.comparingDouble(this::scoreOf))
                .map(DetectionDTO::getLabel)
                .map(String::trim)
                .orElse(null);
    }

    private double scoreOf(DetectionDTO detection) {
        return detection.getScore() != null ? detection.getScore() : 0.0;
    }
}
