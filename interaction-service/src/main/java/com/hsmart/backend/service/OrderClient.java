package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.OrderSummary;
import java.util.Optional;

public interface OrderClient {
    Optional<OrderSummary> findLatestOrder(String userId);
}
