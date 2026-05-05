package com.hsmart.backend.infrastructure.messaging;

import com.hsmart.backend.application.dto.ReviewCreatedEvent;
import com.hsmart.backend.infrastructure.config.ReviewRabbitMqConfig;
import com.hsmart.backend.service.UserTrustScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewCreatedEventListener {

    private final UserTrustScoreService userTrustScoreService;

    @RabbitListener(queues = ReviewRabbitMqConfig.REVIEW_TRUST_UPDATE_QUEUE)
    public void handleReviewCreatedEvent(ReviewCreatedEvent event) {
        Assert.notNull(event, "Review created event must not be null");
        Assert.hasText(event.getSellerId(), "Review created event sellerId must not be blank");
        Assert.notNull(event.getRating(), "Review created event rating must not be null");

        userTrustScoreService.applyReviewRating(event.getSellerId(), event.getRating());
        log.info("Processed review created event for seller {}", event.getSellerId());
    }
}
