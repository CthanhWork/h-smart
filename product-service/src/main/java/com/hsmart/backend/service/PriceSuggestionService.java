package com.hsmart.backend.service;

import java.math.BigDecimal;
import java.util.Optional;

public interface PriceSuggestionService {
    Optional<BigDecimal> findSuggestedPrice(String label);
    void storeAveragePrice(String label, BigDecimal averagePrice);
}
