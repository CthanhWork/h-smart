package com.hsmart.admin.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A single system-account ledger entry (from payment-service). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemLedgerEntryDTO {
    private Long id;
    private String entryType;
    private String direction;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String referenceType;
    private Long referenceId;
    private String description;
    private LocalDateTime createdAt;
}
