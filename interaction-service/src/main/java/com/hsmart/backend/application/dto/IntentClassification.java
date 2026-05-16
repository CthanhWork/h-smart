package com.hsmart.backend.application.dto;

public record IntentClassification(
        Intent intent,
        String reason
) {

    public static IntentClassification general(String reason) {
        return new IntentClassification(Intent.GENERAL, reason);
    }

    public enum Intent {
        SYSTEM,
        POLICY,
        GENERAL
    }
}
