package com.hsmart.order.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GhtkCreateOrderResponseDTO(
        boolean success,
        String message,
        Order order,
        Error error
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Order(
            String label
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(
            String code,
            @JsonProperty("ghtk_label") String ghtkLabel
    ) {
    }
}
