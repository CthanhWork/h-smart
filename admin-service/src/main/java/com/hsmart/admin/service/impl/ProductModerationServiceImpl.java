package com.hsmart.admin.service.impl;

import com.hsmart.admin.application.dto.DetectionDTO;
import com.hsmart.admin.application.dto.ProductCreatedEvent;
import com.hsmart.admin.domain.entities.AdminNotification;
import com.hsmart.admin.infrastructure.persistence.AdminNotificationRepository;
import com.hsmart.admin.service.ProductAdminClient;
import com.hsmart.admin.service.ProductModerationService;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductModerationServiceImpl implements ProductModerationService {

    private static final double MIN_CONFIDENCE = 0.6;
    private static final String APPROVED_STATUS = "APPROVED";
    private static final String PENDING_REVIEW_STATUS = "PENDING_REVIEW";
    private static final String NOTIFICATION_TYPE = "PRODUCT_PENDING_REVIEW";
    private static final Map<String, Set<String>> CATEGORY_ALIASES = Map.of(
            "chair", Set.of("chair", "ghe"),
            "washingmachine", Set.of("washingmachine", "maygiat", "washer"),
            "refrigerator", Set.of("refrigerator", "tulanh", "fridge"),
            "microwave", Set.of("microwave", "lovisong"),
            "fan", Set.of("fan", "quat")
    );

    private final ProductAdminClient productAdminClient;
    private final AdminNotificationRepository adminNotificationRepository;

    @Override
    public void moderateProduct(ProductCreatedEvent event) {
        if (event == null || event.getId() == null) {
            log.warn("Ignored product created event because the payload is missing required identifiers");
            return;
        }

        ModerationDecision decision = evaluate(event);
        productAdminClient.updateModerationStatus(event.getId(), decision.status());

        if (PENDING_REVIEW_STATUS.equals(decision.status())) {
            saveAdminNotification(event, decision.reason());
            log.warn(
                    "Auto-rejected product {} for admin review. Reason: {}",
                    event.getId(),
                    decision.reason()
            );
            return;
        }

        log.info("Auto-approved product {} because AI metadata matched the selected category", event.getId());
    }

    private ModerationDecision evaluate(ProductCreatedEvent event) {
        DetectionDTO bestDetection = event.getAiMetadata() == null ? null : event.getAiMetadata().stream()
                .filter(detection -> StringUtils.hasText(detection.getLabel()))
                .max(Comparator.comparingDouble(detection -> detection.getScore() == null ? 0.0 : detection.getScore()))
                .orElse(null);

        if (bestDetection == null) {
            return new ModerationDecision(PENDING_REVIEW_STATUS, "AI did not detect a product category");
        }

        double confidence = bestDetection.getScore() == null ? 0.0 : bestDetection.getScore();
        if (confidence < MIN_CONFIDENCE) {
            return new ModerationDecision(PENDING_REVIEW_STATUS, "AI confidence is below the moderation threshold");
        }

        if (!StringUtils.hasText(event.getCategoryName())) {
            return new ModerationDecision(PENDING_REVIEW_STATUS, "The selected product category is missing");
        }

        if (!categoryMatches(bestDetection.getLabel(), event.getCategoryName())) {
            return new ModerationDecision(PENDING_REVIEW_STATUS, "AI category does not match the selected category");
        }

        return new ModerationDecision(APPROVED_STATUS, "AI category matched the selected category");
    }

    private boolean categoryMatches(String aiLabel, String selectedCategory) {
        String normalizedAiLabel = normalize(aiLabel);
        String normalizedCategory = normalize(selectedCategory);
        if (normalizedAiLabel.equals(normalizedCategory)) {
            return true;
        }

        Set<String> aliases = CATEGORY_ALIASES.get(normalizedAiLabel);
        return aliases != null && aliases.contains(normalizedCategory);
    }

    private void saveAdminNotification(ProductCreatedEvent event, String reason) {
        String title = StringUtils.hasText(event.getTitle()) ? event.getTitle().trim() : "Untitled product";
        AdminNotification notification = AdminNotification.builder()
                .productId(event.getId())
                .title(title)
                .type(NOTIFICATION_TYPE)
                .message("Product " + event.getId() + " requires admin review. Reason: " + reason)
                .build();
        adminNotificationRepository.save(notification);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        String normalized = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.replaceAll("[^a-z0-9]+", "");
    }

    private record ModerationDecision(String status, String reason) {
    }
}
