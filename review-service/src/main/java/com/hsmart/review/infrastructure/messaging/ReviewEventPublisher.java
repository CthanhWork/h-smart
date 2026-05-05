package com.hsmart.review.infrastructure.messaging;

import com.hsmart.review.application.dto.ReviewCreatedEvent;
import com.hsmart.review.infrastructure.config.ReviewRabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishReviewCreated(ReviewCreatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    ReviewRabbitMqConfig.REVIEW_EXCHANGE,
                    ReviewRabbitMqConfig.REVIEW_CREATED_ROUTING_KEY,
                    event
            );
            log.info("Published review created event for seller {} with rating {}", event.getSellerId(), event.getRating());
        } catch (RuntimeException exception) {
            log.error("Failed to publish review created event for seller {} after retry attempts",
                    event.getSellerId(), exception);
        }
    }
}
