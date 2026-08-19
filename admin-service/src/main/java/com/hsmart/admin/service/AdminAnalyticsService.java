package com.hsmart.admin.service;

import com.hsmart.admin.application.dto.OverviewStatsResponseDTO;
import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.application.dto.SystemAccountSummaryDTO;
import com.hsmart.admin.application.dto.SystemLedgerEntryDTO;

public interface AdminAnalyticsService {
    OverviewStatsResponseDTO getOverviewStats();

    /** Balance/revenue summary of the platform system account. */
    SystemAccountSummaryDTO getSystemAccount();

    /** Paginated system-account ledger history. */
    PageResponseDTO<SystemLedgerEntryDTO> getSystemLedger(int page, int size);
}
