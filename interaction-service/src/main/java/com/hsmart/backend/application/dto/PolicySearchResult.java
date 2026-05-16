package com.hsmart.backend.application.dto;

public record PolicySearchResult(
        String title,
        String content,
        String category,
        double score
) {
}
