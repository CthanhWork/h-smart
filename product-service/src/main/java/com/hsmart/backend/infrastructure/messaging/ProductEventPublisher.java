package com.hsmart.backend.infrastructure.messaging;

import com.hsmart.backend.application.dto.ProductSearchEvent;
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

    public void publishProductCreated(ProductSearchEvent event) {
        publishProductSearchEvent(event, ProductRabbitMqConfig.PRODUCT_CREATED_ROUTING_KEY, "created");
    }

    public void publishProductUpdated(ProductSearchEvent event) {
        publishProductSearchEvent(event, ProductRabbitMqConfig.PRODUCT_UPDATED_ROUTING_KEY, "updated");
    }

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

    private void publishProductSearchEvent(ProductSearchEvent event, String routingKey, String eventName) {
        try {
            rabbitTemplate.convertAndSend(ProductRabbitMqConfig.PRODUCT_EXCHANGE, routingKey, event);
            log.info("Published product {} search event for product {}", eventName, event.getId());
        } catch (RuntimeException exception) {
            log.error("Failed to publish product {} search event for product {} after retry attempts",
                    eventName, event.getId(), exception);
        }
    }
}
