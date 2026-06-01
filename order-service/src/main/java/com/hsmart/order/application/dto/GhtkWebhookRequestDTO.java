package com.hsmart.order.application.dto;

import java.util.List;
import java.util.Map;
import org.springframework.util.MultiValueMap;

public record GhtkWebhookRequestDTO(
        String labelId,
        String partnerId,
        Integer statusId,
        String actionTime,
        String reasonCode,
        String reason
) {
    public static GhtkWebhookRequestDTO from(MultiValueMap<String, String> payload) {
        return new GhtkWebhookRequestDTO(
                payload.getFirst("label_id"),
                payload.getFirst("partner_id"),
                parseStatusId(payload.getFirst("status_id")),
                payload.getFirst("action_time"),
                payload.getFirst("reason_code"),
                payload.getFirst("reason")
        );
    }

    public static Map<String, List<String>> withoutHash(MultiValueMap<String, String> payload) {
        Map<String, List<String>> sanitizedPayload = new java.util.LinkedHashMap<>(payload);
        sanitizedPayload.remove("hash");
        return sanitizedPayload;
    }

    private static Integer parseStatusId(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
