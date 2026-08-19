package com.hsmart.search.infrastructure.messaging;

import com.hsmart.search.application.dto.ProductSearchEvent;
import com.hsmart.search.infrastructure.config.ProductRabbitMqConfig;
import com.hsmart.search.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSearchEventListener {

    private final ProductSearchService productSearchService;

    @RabbitListener(queues = ProductRabbitMqConfig.PRODUCT_SEARCH_INDEX_QUEUE)
    public void handleProductSearchEvent(ProductSearchEvent event) {
        productSearchService.indexProduct(event);
        if (event != null) {
            log.info("Processed product search index event for product {}", event.getId());
        }
    }

    @RabbitListener(queues = ProductRabbitMqConfig.PRODUCT_SEARCH_DELETE_QUEUE)
    public void handleProductDeleteEvent(ProductSearchEvent event) {
        if (event == null) {
            return;
        }
        productSearchService.deleteProduct(event.getId());
        log.info("Processed product search delete event for product {}", event.getId());
    }
}
