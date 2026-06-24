package com.hsmart.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.hsmart.backend.application.dto.DetectionDTO;
import com.hsmart.backend.application.dto.ImageAnalysisResponseDTO;
import com.hsmart.backend.application.dto.PredictResponseDTO;
import com.hsmart.backend.application.dto.ProductListingSuggestionResponseDTO;
import com.hsmart.backend.infrastructure.context.UserContextHolder;
import com.hsmart.backend.service.ProductImageAnalysisService;
import com.hsmart.backend.service.ProductListingSuggestionService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductListingSuggestionServiceImpl implements ProductListingSuggestionService {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";
    private static final String USER_HEADER = "X-User-Id";
    private static final String DEFAULT_CATEGORY = "Household product";
    private static final String DEFAULT_CONDITION = "Used";
    private static final int MAX_SUGGESTED_TITLE_LENGTH = 120;

    // Varied, natural-sounding listing titles. The recognized name is always placed first so the
    // sentence never breaks Vietnamese capitalization, and a template is chosen deterministically
    // from the name so the same product type reads consistently (and stays unit-testable).
    private static final List<String> TITLE_TEMPLATES = List.of(
            "%s cũ, còn dùng tốt – cần thanh lý",
            "%s đã qua sử dụng, còn đẹp và hoạt động ổn định",
            "%s second-hand, giá tốt cho người cần dùng",
            "%s cũ thanh lý, hợp cho gia đình hoặc nhà trọ"
    );

    private final ProductImageAnalysisService productImageAnalysisService;
    private final RestTemplate restTemplate;

    @Value("${services.interaction.base-url:http://localhost:8083}")
    private String interactionServiceBaseUrl;

    @Value("${internal.security.secret:}")
    private String internalSharedSecret;

    @Value("${product.naming.confident-threshold:0.6}")
    private double confidentThreshold;

    @Override
    public ProductListingSuggestionResponseDTO prepareListing(MultipartFile file) {
        ImageAnalysisResponseDTO imageAnalysis = productImageAnalysisService.analyzeImage(file);
        String suggestedTitle = buildDetailedSuggestedTitle(imageAnalysis);
        String suggestedDescription = generateDescription(suggestedTitle, imageAnalysis);

        log.info("Prepared product listing suggestions with titleAvailable={} and descriptionAvailable={}",
                StringUtils.hasText(suggestedTitle), StringUtils.hasText(suggestedDescription));
        return ProductListingSuggestionResponseDTO.builder()
                .suggestedTitle(suggestedTitle)
                .suggestedDescription(suggestedDescription)
                .build();
    }

    private String buildDetailedSuggestedTitle(ImageAnalysisResponseDTO imageAnalysis) {
        String baseName = normalizeText(imageAnalysis.getSuggestedName());
        if (!StringUtils.hasText(baseName)) {
            // AI recognized nothing — never fabricate a title, let the seller name it themselves.
            return "";
        }

        // Low-confidence guess: offer only the bare name so we do not dress up an uncertain detection.
        if (bestConfidence(imageAnalysis.getAiMetadata()) < confidentThreshold) {
            return limitLength(baseName, MAX_SUGGESTED_TITLE_LENGTH);
        }

        return limitLength(naturalTitle(baseName), MAX_SUGGESTED_TITLE_LENGTH);
    }

    private String naturalTitle(String baseName) {
        int index = Math.floorMod(baseName.toLowerCase(Locale.ROOT).hashCode(), TITLE_TEMPLATES.size());
        return String.format(TITLE_TEMPLATES.get(index), baseName);
    }

    private double bestConfidence(PredictResponseDTO aiMetadata) {
        if (aiMetadata == null) {
            return 0.0;
        }
        double best = aiMetadata.getConfidence() != null ? aiMetadata.getConfidence() : 0.0;
        if (aiMetadata.getDetections() != null) {
            for (DetectionDTO detection : aiMetadata.getDetections()) {
                if (detection.getScore() != null) {
                    best = Math.max(best, detection.getScore());
                }
            }
        }
        return best;
    }

    private String generateDescription(String suggestedTitle, ImageAnalysisResponseDTO imageAnalysis) {
        if (!StringUtils.hasText(suggestedTitle)) {
            return "";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(internalSharedSecret)) {
                headers.set(INTERNAL_SECRET_HEADER, internalSharedSecret);
            }
            UserContextHolder.getCurrentUserId()
                    .filter(StringUtils::hasText)
                    .ifPresent(userId -> headers.set(USER_HEADER, userId));

            Map<String, Object> body = Map.of(
                    "productName", suggestedTitle,
                    "category", resolveCategory(imageAnalysis),
                    "condition", DEFAULT_CONDITION,
                    "price", resolvePrice(imageAnalysis)
            );

            JsonNode response = restTemplate.postForObject(
                    trimTrailingSlash(interactionServiceBaseUrl) + "/api/v1/assistant/generate-description",
                    new HttpEntity<>(body, headers),
                    JsonNode.class
            );

            return normalizeText(response != null
                    ? response.path("data").path("generatedDescription").asText("")
                    : "");
        } catch (RuntimeException exception) {
            log.warn("Product listing description suggestion failed. Returning title suggestion only. Reason: {}",
                    exception.getMessage());
            return "";
        }
    }

    private String resolveCategory(ImageAnalysisResponseDTO imageAnalysis) {
        if (imageAnalysis.getAiMetadata() == null) {
            return DEFAULT_CATEGORY;
        }
        String translatedLabel = normalizeText(imageAnalysis.getAiMetadata().getTranslatedLabel());
        if (StringUtils.hasText(translatedLabel)) {
            return translatedLabel;
        }
        String label = normalizeText(imageAnalysis.getAiMetadata().getLabel());
        return StringUtils.hasText(label) ? label : DEFAULT_CATEGORY;
    }

    private BigDecimal resolvePrice(ImageAnalysisResponseDTO imageAnalysis) {
        return imageAnalysis.getSuggestedPrice() != null ? imageAnalysis.getSuggestedPrice() : BigDecimal.ZERO;
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String limitLength(String value, int maxLength) {
        String normalized = normalizeText(value).replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength).trim();
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("/+$", "");
    }
}
