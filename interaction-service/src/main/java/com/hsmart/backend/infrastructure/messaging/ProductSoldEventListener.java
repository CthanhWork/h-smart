package com.hsmart.backend.infrastructure.messaging;

import com.hsmart.backend.application.dto.NotificationRequestDTO;
import com.hsmart.backend.application.dto.ProductSoldEvent;
import com.hsmart.backend.infrastructure.config.ProductRabbitMqConfig;
import com.hsmart.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSoldEventListener {

    private static final String PRODUCT_SOLD_NOTIFICATION_TYPE = "PRODUCT_SOLD";

    private final NotificationService notificationService;

    @RabbitListener(queues = ProductRabbitMqConfig.PRODUCT_SOLD_NOTIFICATION_QUEUE)
    public void handleProductSoldEvent(ProductSoldEvent event) {
        if (event == null || event.getProductId() == null || !StringUtils.hasText(event.getSellerId())) {
            log.warn("Ignored invalid product sold event");
            return;
        }

        String title = StringUtils.hasText(event.getTitle()) ? event.getTitle().trim() : "Untitled product";
        String message = String.format("Sản phẩm \"%s\" của bạn đã được đánh dấu là đã bán.", title);
        NotificationRequestDTO request = NotificationRequestDTO.builder()
                .userId(event.getSellerId().trim())
                .title("Sản phẩm đã bán")
                .type(PRODUCT_SOLD_NOTIFICATION_TYPE)
                .message(message)
                .productId(event.getProductId())
                .build();

        notificationService.createNotification(request);
        log.info("Created product sold notification for seller {} and product {}",
                request.getUserId(), request.getProductId());
    }
}
