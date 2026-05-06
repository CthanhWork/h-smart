package com.hsmart.admin.infrastructure.messaging;

import com.hsmart.admin.application.dto.ProductCreatedEvent;
import com.hsmart.admin.infrastructure.config.ProductRabbitMqConfig;
import com.hsmart.admin.service.ProductModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCreatedEventListener {

    private final ProductModerationService productModerationService;

    @RabbitListener(queues = ProductRabbitMqConfig.PRODUCT_MODERATION_QUEUE)
    public void onProductCreated(@Payload ProductCreatedEvent event) {
        productModerationService.moderateProduct(event);
        log.info("Processed product moderation event for product {}", event != null ? event.getId() : null);
    }
}
