package com.hsmart.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.hsmart.backend.application.dto.ImageAnalysisResponseDTO;
import com.hsmart.backend.application.dto.ProductListingSuggestionResponseDTO;
import com.hsmart.backend.infrastructure.context.UserContextHolder;
import com.hsmart.backend.service.ProductImageAnalysisService;
import com.hsmart.backend.service.ProductListingSuggestionService;
import java.math.BigDecimal;
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

    private final ProductImageAnalysisService productImageAnalysisService;
    private final RestTemplate restTemplate;

    @Value("${services.interaction.base-url:http://localhost:8083}")
    private String interactionServiceBaseUrl;

    @Value("${internal.security.secret:}")
    private String internalSharedSecret;

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
            return "";
        }

        String category = resolveCategory(imageAnalysis);
        String title = isSameText(baseName, category)
                ? baseName + " đã qua sử dụng, phù hợp dùng trong gia đình"
                : baseName + " đã qua sử dụng, nhóm " + category.toLowerCase() + ", phù hợp dùng trong gia đình";
        return limitLength(title, MAX_SUGGESTED_TITLE_LENGTH);
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

    private boolean isSameText(String first, String second) {
        return normalizeText(first).equalsIgnoreCase(normalizeText(second));
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
