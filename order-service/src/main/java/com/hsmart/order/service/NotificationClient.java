package com.hsmart.order.service;

import com.hsmart.order.application.dto.OfferResponseDTO;

public interface NotificationClient {
    void sendOfferNotification(OfferResponseDTO offer);
    void sendOfferAcceptedNotification(OfferResponseDTO offer);
    void sendOfferRejectedNotification(OfferResponseDTO offer);
    void sendOfferCancelledNotification(OfferResponseDTO offer);
    void sendOfferProductUnavailableNotification(OfferResponseDTO offer);
}
