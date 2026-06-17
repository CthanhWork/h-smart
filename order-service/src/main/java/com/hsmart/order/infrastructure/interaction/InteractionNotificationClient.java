package com.hsmart.order.infrastructure.interaction;

import com.hsmart.order.application.dto.NotificationRequestDTO;
import com.hsmart.order.application.dto.OfferResponseDTO;
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
                "PRODUCT_OFFER",
                "A buyer submitted an offer for your product."
        );
    }

    @Override
    public void sendOfferAcceptedNotification(OfferResponseDTO offer) {
        sendNotification(
                offer,
                offer.getBuyerId(),
                "PRODUCT_OFFER_ACCEPTED",
                "Your offer was accepted by the seller."
        );
    }

    @Override
    public void sendOfferRejectedNotification(OfferResponseDTO offer) {
        sendNotification(
                offer,
                offer.getBuyerId(),
                "PRODUCT_OFFER_REJECTED",
                "Your offer was rejected by the seller."
        );
    }

    @Override
    public void sendOfferCancelledNotification(OfferResponseDTO offer) {
        sendNotification(
                offer,
                offer.getSellerId(),
                "PRODUCT_OFFER_CANCELLED",
                "A buyer cancelled an offer for your product."
        );
    }

    @Override
    public void sendOfferProductUnavailableNotification(OfferResponseDTO offer) {
        sendNotification(
                offer,
                offer.getBuyerId(),
                "PRODUCT_OFFER_UNAVAILABLE",
                "A product you made an offer on is no longer available."
        );
    }

    private void sendNotification(OfferResponseDTO offer, String userId, String type, String message) {
        try {
            RestClient.RequestBodySpec request = interactionServiceRestClient.post()
                    .uri("/api/v1/interactions/notifications");
            if (StringUtils.hasText(internalSharedSecret)) {
                request.header(INTERNAL_SECRET_HEADER, internalSharedSecret);
            }
            request.body(NotificationRequestDTO.builder()
                            .userId(userId)
                            .type(type)
                            .message(message)
                            .productId(offer.getProductId())
                            .build())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("Failed to send {} notification for offer {}", type, offer.getId(), exception);
        }
    }
}
