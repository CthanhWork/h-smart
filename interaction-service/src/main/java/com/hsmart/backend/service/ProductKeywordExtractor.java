package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.ProductCatalogItem;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProductKeywordExtractor {

    private static final Map<String, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put("máy giặt", List.of("máy giặt", "may giat", "washing machine"));
        KEYWORDS.put("tủ lạnh", List.of("tủ lạnh", "tu lanh", "fridge", "refrigerator"));
        KEYWORDS.put("ghế", List.of("ghế", "ghe", "chair"));
        KEYWORDS.put("bàn", List.of("bàn", "ban", "table", "desk"));
        KEYWORDS.put("sofa", List.of("sofa", "ghế sofa", "ghe sofa"));
        KEYWORDS.put("điều hòa", List.of("điều hòa", "dieu hoa", "máy lạnh", "may lanh", "air conditioner"));
        KEYWORDS.put("quạt", List.of("quạt", "quat", "fan"));
        KEYWORDS.put("nồi cơm", List.of("nồi cơm", "noi com", "rice cooker"));
        KEYWORDS.put("bếp", List.of("bếp", "bep", "stove", "cooktop"));
        KEYWORDS.put("tivi", List.of("tivi", "tv", "television"));
        KEYWORDS.put("lò vi sóng", List.of("lò vi sóng", "lo vi song", "microwave"));
        KEYWORDS.put("giường", List.of("giường", "giuong", "bed"));
        KEYWORDS.put("kệ", List.of("kệ", "ke", "shelf", "rack"));
    }

    public List<String> extractKeywords(String message) {
        String normalizedMessage = normalize(message);

        return KEYWORDS.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .map(this::normalize)
                        .anyMatch(normalizedMessage::contains))
                .map(Map.Entry::getKey)
                .toList();
    }

    public boolean matchesAnyKeyword(ProductCatalogItem item, List<String> keywords) {
        String searchableText = normalize(String.join(" ",
                nullToEmpty(item.title()),
                nullToEmpty(item.description()),
                nullToEmpty(item.categoryName())
        ));

        return keywords.stream()
                .map(this::normalize)
                .anyMatch(searchableText::contains);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return normalized.replace('đ', 'd');
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
