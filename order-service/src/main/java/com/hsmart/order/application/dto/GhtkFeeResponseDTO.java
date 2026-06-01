package com.hsmart.order.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GhtkFeeResponseDTO(
        boolean success,
        String message,
        Fee fee
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fee(
            BigDecimal fee
    ) {
    }
}
