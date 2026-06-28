package com.hsmart.payment.application.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Aggregate view of the platform system account for admin dashboards. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemAccountSummaryDTO {
    /** Current wallet balance. */
    private BigDecimal balance;
    /** Sum of all CREDIT entries (total platform fee revenue collected). */
    private BigDecimal totalCredited;
    /** Sum of all DEBIT entries (withdrawals/adjustments). */
    private BigDecimal totalDebited;
    /** Number of ledger entries. */
    private long entryCount;
}
