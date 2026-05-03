package com.hsmart.backend.infrastructure.messaging;

import com.hsmart.backend.application.dto.ProductSoldEvent;
import com.hsmart.backend.infrastructure.config.ProductRabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishProductSold(ProductSoldEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    ProductRabbitMqConfig.PRODUCT_EXCHANGE,
                    ProductRabbitMqConfig.PRODUCT_SOLD_ROUTING_KEY,
                    event
            );
            log.info("Published product sold event for product {} and seller {}",
                    event.getProductId(), event.getSellerId());
        } catch (RuntimeException exception) {
            log.error("Failed to publish product sold event for product {} after retry attempts",
                    event.getProductId(), exception);
        }
    }
}
