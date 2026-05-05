package com.hsmart.backend.infrastructure.messaging;

import com.hsmart.backend.application.dto.OrderCompletedEvent;
import com.hsmart.backend.infrastructure.config.ProductRabbitMqConfig;
import com.hsmart.backend.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCompletedEventListener {

    private final ProductService productService;

    @RabbitListener(queues = ProductRabbitMqConfig.ORDER_PRODUCT_UPDATE_QUEUE)
    public void handleOrderCompletedEvent(OrderCompletedEvent event) {
        Assert.notNull(event, "Order completed event must not be null");
        Assert.notNull(event.getProductId(), "Order completed event productId must not be null");

        productService.markProductSoldFromOrderEvent(event.getProductId());
        log.info("Processed order completed event for product {}", event.getProductId());
    }
}
