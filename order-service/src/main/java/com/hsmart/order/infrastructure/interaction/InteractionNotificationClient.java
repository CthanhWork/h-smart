package com.hsmart.order.infrastructure.interaction;

import com.hsmart.order.application.dto.NotificationRequestDTO;
import com.hsmart.order.application.dto.OfferResponseDTO;
import com.hsmart.order.application.dto.OrderResponseDTO;
import com.hsmart.order.service.NotificationClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class InteractionNotificationClient implements NotificationClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient interactionServiceRestClient;
    private final String internalSharedSecret;

    public InteractionNotificationClient(
            @Qualifier("interactionServiceRestClient") RestClient interactionServiceRestClient,
            @Value("${internal.security.secret:}") String internalSharedSecret
    ) {
        this.interactionServiceRestClient = interactionServiceRestClient;
        this.internalSharedSecret = internalSharedSecret;
    }

    @Override
    public void sendOfferNotification(OfferResponseDTO offer) {
        sendNotification(
                offer,
                offer.getSellerId(),
                "Đề nghị giá mới",
                "PRODUCT_OFFER",
                "Bạn vừa nhận được một đề nghị giá mới cho sản phẩm của mình."
        );
    }

    @Override
    public void sendOfferAcceptedNotification(OfferResponseDTO offer) {
        sendNotification(
                offer,
                offer.getBuyerId(),
                "Đề nghị giá được chấp nhận",
                "PRODUCT_OFFER_ACCEPTED",
                "Đề nghị giá của bạn đã được người bán chấp nhận."
        );
    }

    @Override
    public void sendOfferRejectedNotification(OfferResponseDTO offer) {
        sendNotification(
                offer,
                offer.getBuyerId(),
                "Đề nghị giá bị từ chối",
                "PRODUCT_OFFER_REJECTED",
                "Đề nghị giá của bạn đã bị người bán từ chối."
        );
    }

    @Override
    public void sendOfferCancelledNotification(OfferResponseDTO offer) {
        sendNotification(
                offer,
                offer.getSellerId(),
                "Đề nghị giá đã bị hủy",
                "PRODUCT_OFFER_CANCELLED",
                "Người mua đã hủy một đề nghị giá cho sản phẩm của bạn."
        );
    }

    @Override
    public void sendOfferProductUnavailableNotification(OfferResponseDTO offer) {
        sendNotification(
                offer,
                offer.getBuyerId(),
                "Sản phẩm không còn khả dụng",
                "PRODUCT_OFFER_UNAVAILABLE",
                "Sản phẩm bạn đã trả giá hiện không còn khả dụng."
        );
    }

    @Override
    public void sendOfferExpiredNotification(OfferResponseDTO offer) {
        sendNotification(
                offer,
                offer.getBuyerId(),
                "Đề nghị giá đã hết hạn",
                "PRODUCT_OFFER_EXPIRED",
                "Đề nghị giá của bạn đã hết hạn."
        );
    }

    @Override
    public void sendOrderCancelledNotification(OrderResponseDTO order) {
        sendOrderNotification(
                order.getBuyerId(),
                "Đơn hàng đã bị hủy",
                "ORDER_CANCELLED",
                "Đơn hàng của bạn đã bị hủy.",
                order
        );
        sendOrderNotification(
                order.getSellerId(),
                "Đơn hàng cho sản phẩm của bạn đã bị hủy",
                "ORDER_CANCELLED_SELLER",
                "Một đơn hàng cho sản phẩm của bạn đã bị hủy.",
                order
        );
    }

    private void sendOrderNotification(String userId, String title, String type, String message, OrderResponseDTO order) {
        try {
            RestClient.RequestBodySpec request = interactionServiceRestClient.post()
                    .uri("/api/v1/interactions/notifications");
            if (StringUtils.hasText(internalSharedSecret)) {
                request.header(INTERNAL_SECRET_HEADER, internalSharedSecret);
            }
            request.body(NotificationRequestDTO.builder()
                            .userId(userId)
                            .title(title)
                            .type(type)
                            .message(message)
                            .productId(order.getProductId())
                            .orderId(order.getId())
                            .build())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("Failed to send {} notification to user {}", type, userId, exception);
        }
    }

    private void sendNotification(OfferResponseDTO offer, String userId, String title, String type, String message) {
        try {
            RestClient.RequestBodySpec request = interactionServiceRestClient.post()
                    .uri("/api/v1/interactions/notifications");
            if (StringUtils.hasText(internalSharedSecret)) {
                request.header(INTERNAL_SECRET_HEADER, internalSharedSecret);
            }
            request.body(NotificationRequestDTO.builder()
                            .userId(userId)
                            .title(title)
                            .type(type)
                            .message(message)
                            .productId(offer.getProductId())
                            .offerId(offer.getId())
                            .build())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("Failed to send {} notification for offer {}", type, offer.getId(), exception);
        }
    }
}
