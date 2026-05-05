package com.hsmart.order.infrastructure.messaging;

import com.hsmart.order.application.dto.OrderCompletedEvent;
import com.hsmart.order.infrastructure.config.OrderRabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishOrderCompleted(OrderCompletedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    OrderRabbitMqConfig.ORDER_EXCHANGE,
                    OrderRabbitMqConfig.ORDER_COMPLETED_ROUTING_KEY,
                    event
            );
            log.info("Published order completed event for product {}", event.getProductId());
        } catch (RuntimeException exception) {
            log.error("Failed to publish order completed event for product {} after retry attempts",
                    event.getProductId(), exception);
        }
    }
}
