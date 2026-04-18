package com.hsmart.backend.application.mapper;

import com.hsmart.backend.application.dto.DetectionDTO;
import com.hsmart.backend.application.dto.ProductDTO;
import com.hsmart.backend.application.dto.ProductResponseDTO;
import com.hsmart.backend.domain.entities.Product;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

public final class ProductMapper {

    private static final Map<String, String> LABEL_TRANSLATIONS = Map.ofEntries(
            Map.entry("microwave", "Lo vi song"),
            Map.entry("microwave_oven", "Lo vi song"),
            Map.entry("refrigerator", "Tu lanh"),
            Map.entry("automatic_washer", "May giat"),
            Map.entry("washing_machine", "May giat"),
            Map.entry("chair", "Ghe"),
            Map.entry("table", "Ban"),
            Map.entry("sofa", "Ghe sofa"),
            Map.entry("television_set", "Tivi"),
            Map.entry("air_conditioner", "May lanh"),
            Map.entry("oven", "Lo nuong"),
            Map.entry("lamp", "Den"),
            Map.entry("cabinet", "Tu"),
            Map.entry("bench", "Ghe bang"),
            Map.entry("sink", "Bon rua")
    );

    private ProductMapper() {
    }

    public static Product toEntity(ProductDTO productDto, String resolvedName, String imageUrl, String aiMetadataJson) {
        return Product.builder()
                .name(resolvedName)
                .description(productDto.getDescription())
                .price(productDto.getPrice())
                .imageUrl(imageUrl)
                .aiMetadata(aiMetadataJson)
                .build();
    }

    public static ProductResponseDTO toResponse(Product product, List<DetectionDTO> aiMetadata) {
        return ProductResponseDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .imageUrl(toAbsoluteImageUrl(null, product.getImageUrl()))
                .aiMetadata(aiMetadata)
                .numDetections(aiMetadata.size())
                .build();
    }

    public static ProductResponseDTO toResponse(Product product, List<DetectionDTO> aiMetadata, String publicBaseUrl) {
        return ProductResponseDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .imageUrl(toAbsoluteImageUrl(publicBaseUrl, product.getImageUrl()))
                .aiMetadata(aiMetadata)
                .numDetections(aiMetadata.size())
                .build();
    }

    public static String resolveProductName(String requestedName, List<DetectionDTO> detections) {
        if (StringUtils.hasText(requestedName)) {
            return requestedName.trim();
        }

        return detections.stream()
                .filter(detection -> detection.getLabel() != null)
                .max((left, right) -> Double.compare(
                        left.getScore() != null ? left.getScore() : 0.0,
                        right.getScore() != null ? right.getScore() : 0.0
                ))
                .map(DetectionDTO::getLabel)
                .map(ProductMapper::translateLabel)
                .orElse("San pham khong ro");
    }

    private static String translateLabel(String label) {
        String normalized = label.toLowerCase(Locale.ROOT);
        if (LABEL_TRANSLATIONS.containsKey(normalized)) {
            return LABEL_TRANSLATIONS.get(normalized);
        }

        String humanized = normalized.replace('_', ' ').replace('-', ' ');
        if (humanized.isBlank()) {
            return "San pham khong ro";
        }

        return Character.toUpperCase(humanized.charAt(0)) + humanized.substring(1);
    }

    private static String toAbsoluteImageUrl(String publicBaseUrl, String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return imageUrl;
        }

        String normalizedImageUrl = imageUrl.trim();
        if (normalizedImageUrl.startsWith("http://") || normalizedImageUrl.startsWith("https://")) {
            return normalizedImageUrl;
        }

        if (!StringUtils.hasText(publicBaseUrl)) {
            return normalizedImageUrl;
        }

        String normalizedBase = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        String normalizedPath = normalizedImageUrl.startsWith("/")
                ? normalizedImageUrl
                : "/" + normalizedImageUrl;

        return normalizedBase + normalizedPath;
    }
}
