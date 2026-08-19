package com.hsmart.payment.application.dto;

import com.hsmart.payment.domain.entities.LedgerDirection;
import com.hsmart.payment.domain.entities.LedgerEntryType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A single system-account ledger entry, exposed to admin. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemLedgerEntryDTO {
    private Long id;
    private LedgerEntryType entryType;
    private LedgerDirection direction;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String referenceType;
    private Long referenceId;
    private String description;
    private LocalDateTime createdAt;
}
