package com.hsmart.admin.presentation.controllers;

import com.hsmart.admin.application.dto.ApiResponse;
import com.hsmart.admin.application.dto.ReportRequestDTO;
import com.hsmart.admin.application.dto.ReportResponseDTO;
import com.hsmart.admin.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReportResponseDTO>> submitReport(
            @RequestHeader(name = "X-User-Id", required = false) String reporterId,
            @Valid @RequestBody ReportRequestDTO request
    ) {
        ReportResponseDTO response = reportService.submitReport(reporterId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "Report submitted successfully", response));
    }
}
