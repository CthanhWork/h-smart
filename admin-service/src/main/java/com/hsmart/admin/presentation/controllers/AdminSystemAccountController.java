package com.hsmart.admin.presentation.controllers;

import com.hsmart.admin.application.dto.ApiResponse;
import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.application.dto.SystemAccountSummaryDTO;
import com.hsmart.admin.application.dto.SystemLedgerEntryDTO;
import com.hsmart.admin.service.AdminAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/system-account")
@RequiredArgsConstructor
public class AdminSystemAccountController {

    private final AdminAnalyticsService adminAnalyticsService;

    @GetMapping
    public ResponseEntity<ApiResponse<SystemAccountSummaryDTO>> getSystemAccount() {
        SystemAccountSummaryDTO response = adminAnalyticsService.getSystemAccount();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "System account summary fetched successfully", response));
    }

    @GetMapping("/ledger")
    public ResponseEntity<ApiResponse<PageResponseDTO<SystemLedgerEntryDTO>>> getSystemLedger(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponseDTO<SystemLedgerEntryDTO> response = adminAnalyticsService.getSystemLedger(page, size);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "System account ledger fetched successfully", response));
    }
}
