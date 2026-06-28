package com.hsmart.payment.service;

import com.hsmart.payment.application.dto.PageResponseDTO;
import com.hsmart.payment.application.dto.SystemAccountSummaryDTO;
import com.hsmart.payment.application.dto.SystemLedgerEntryDTO;
import java.math.BigDecimal;

public interface SystemAccountService {

    /**
     * Credits a platform fee to the system account and writes a ledger entry.
     * Idempotent per order: if a {@code PLATFORM_FEE_IN} entry already exists for the order,
     * the call is a no-op.
     */
    void creditPlatformFee(Long orderId, BigDecimal amount, String description);

    /** Aggregate balance/revenue view of the platform system account. */
    SystemAccountSummaryDTO getSummary();

    /** Paginated ledger history, newest first. */
    PageResponseDTO<SystemLedgerEntryDTO> getLedger(int page, int size);
}
