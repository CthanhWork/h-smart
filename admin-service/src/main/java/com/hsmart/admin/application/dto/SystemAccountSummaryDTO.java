package com.hsmart.admin.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Aggregate view of the platform system account (from payment-service). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemAccountSummaryDTO {
    private BigDecimal balance;
    private BigDecimal totalCredited;
    private BigDecimal totalDebited;
    private long entryCount;
}
