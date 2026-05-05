package com.hsmart.review.service;

import com.hsmart.review.application.dto.OrderResponseDTO;

public interface OrderClient {
    OrderResponseDTO getOrder(Long orderId, String buyerId);
}
