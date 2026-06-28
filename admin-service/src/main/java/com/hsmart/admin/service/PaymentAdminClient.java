package com.hsmart.admin.service;

import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.application.dto.SystemAccountSummaryDTO;
import com.hsmart.admin.application.dto.SystemLedgerEntryDTO;

public interface PaymentAdminClient {
    SystemAccountSummaryDTO getSystemAccountSummary();
    PageResponseDTO<SystemLedgerEntryDTO> listLedger(int page, int size);
}
