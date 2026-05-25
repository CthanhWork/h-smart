package com.hsmart.backend.application.dto;

import java.math.BigDecimal;

public interface AveragePriceByLabelProjection {
    String getLabel();
    BigDecimal getAveragePrice();
}
