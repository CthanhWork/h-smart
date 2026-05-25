package com.hsmart.admin.service.impl;

import com.hsmart.admin.application.dto.DetectionDTO;
import com.hsmart.admin.application.dto.ProductCreatedEvent;
import com.hsmart.admin.application.dto.SellerTrustResponseDTO;
import com.hsmart.admin.domain.entities.AdminNotification;
import com.hsmart.admin.infrastructure.persistence.AdminNotificationRepository;
import com.hsmart.admin.service.ProductAdminClient;
import com.hsmart.admin.service.ProductModerationService;
import com.hsmart.admin.service.UserAdminClient;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private static final BigDecimal MIN_TRUST_SCORE = BigDecimal.valueOf(4.5);
    private static final long MIN_REVIEW_COUNT = 5L;
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
    private final UserAdminClient userAdminClient;
    private final AdminNotificationRepository adminNotificationRepository;

    @Override
    public void moderateProduct(ProductCreatedEvent event) {
        try {
            if (event == null || event.getId() == null) {
                log.warn("Ignored product created event because the payload is missing required identifiers");
                return;
            }

            ModerationDecision decision = evaluate(event);
            productAdminClient.updateModerationStatus(event.getId(), decision.status());

            if (PENDING_REVIEW_STATUS.equals(decision.status())) {
                saveAdminNotification(event, decision.reason());
                log.warn("Product {} moved to pending review. Reason: {}", event.getId(), decision.reason());
                return;
            }

            log.info(
                    "Auto-approved product {} because the seller is trusted and AI metadata matched the selected category",
                    event.getId()
            );
        } catch (RuntimeException exception) {
            Long productId = event != null ? event.getId() : null;
            log.error("Product moderation failed for product {}. Falling back to pending review", productId, exception);
            fallbackToPendingReview(event, "Moderation fallback triggered because a downstream service call failed");
        }
    }

    private ModerationDecision evaluate(ProductCreatedEvent event) {
        ImageModerationResult imageResult = evaluateImage(event);
        SellerModerationResult sellerResult = evaluateSeller(event);

        if (imageResult.valid() && sellerResult.valid()) {
            return new ModerationDecision(APPROVED_STATUS, "Seller trust and AI metadata checks passed");
        }

        List<String> reasons = new ArrayList<>();
        if (!imageResult.valid()) {
            reasons.add(imageResult.reason());
        }
        if (!sellerResult.valid()) {
            reasons.add(sellerResult.reason());
        }

        return new ModerationDecision(PENDING_REVIEW_STATUS, String.join("; ", reasons));
    }

    private ImageModerationResult evaluateImage(ProductCreatedEvent event) {
        if (event.getAiMetadata() == null || event.getAiMetadata().isEmpty()) {
            return new ImageModerationResult(false, "AI metadata is missing");
        }

        DetectionDTO bestDetection = event.getAiMetadata().stream()
                .filter(detection -> StringUtils.hasText(detection.getLabel()))
                .max(Comparator.comparingDouble(detection -> detection.getScore() == null ? 0.0 : detection.getScore()))
                .orElse(null);

        if (bestDetection == null) {
            return new ImageModerationResult(false, "AI did not detect a product category");
        }

        double confidence = bestDetection.getScore() == null ? 0.0 : bestDetection.getScore();
        if (confidence < MIN_CONFIDENCE) {
            return new ImageModerationResult(false, "AI confidence is below the moderation threshold");
        }

        if (!StringUtils.hasText(event.getCategoryName())) {
            return new ImageModerationResult(false, "The selected product category is missing");
        }

        if (!categoryMatches(bestDetection.getLabel(), event.getCategoryName())) {
            return new ImageModerationResult(false, "AI category does not match the selected category");
        }

        return new ImageModerationResult(true, "AI category matched the selected category");
    }

    private SellerModerationResult evaluateSeller(ProductCreatedEvent event) {
        if (!StringUtils.hasText(event.getSellerId())) {
            return new SellerModerationResult(false, "Seller id is missing");
        }

        SellerTrustResponseDTO trustProfile = userAdminClient.getSellerTrustProfile(event.getSellerId());
        BigDecimal trustScore = trustProfile.getTrustScore() == null ? BigDecimal.ZERO : trustProfile.getTrustScore();
        long reviewCount = trustProfile.getReviewCount() == null ? 0L : trustProfile.getReviewCount();

        if (trustScore.compareTo(MIN_TRUST_SCORE) >= 0 && reviewCount >= MIN_REVIEW_COUNT) {
            return new SellerModerationResult(true, "Seller trust score and review count passed moderation thresholds");
        }

        return new SellerModerationResult(
                false,
                "Seller trust score or review count is below moderation thresholds"
        );
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

    private void fallbackToPendingReview(ProductCreatedEvent event, String reason) {
        if (event == null || event.getId() == null) {
            return;
        }

        try {
            productAdminClient.updateModerationStatus(event.getId(), PENDING_REVIEW_STATUS);
        } catch (RuntimeException exception) {
            log.error("Fallback pending review status update failed for product {}", event.getId(), exception);
        }

        try {
            saveAdminNotification(event, reason);
        } catch (RuntimeException exception) {
            log.error("Fallback admin notification creation failed for product {}", event.getId(), exception);
        }
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

    private record ImageModerationResult(boolean valid, String reason) {
    }

    private record SellerModerationResult(boolean valid, String reason) {
    }
}
