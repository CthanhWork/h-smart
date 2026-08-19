package com.hsmart.admin.presentation.controllers;

import com.hsmart.admin.application.dto.ApiResponse;
import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.application.dto.ReportActionRequestDTO;
import com.hsmart.admin.application.dto.ReportResponseDTO;
import com.hsmart.admin.domain.entities.ReportStatus;
import com.hsmart.admin.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponseDTO<ReportResponseDTO>>> listReports(
            @RequestParam(required = false) ReportStatus status,
            @ParameterObject
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Reports fetched successfully",
                reportService.listReports(status, pageable)
        ));
    }

    @PostMapping("/{id}/action")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> processReport(
            @PathVariable Long id,
            @RequestHeader(name = "X-User-Id", required = false) String adminUserId,
            @Valid @RequestBody ReportActionRequestDTO request
    ) {
        ReportResponseDTO response = reportService.processReport(id, request, adminUserId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Report processed successfully", response));
    }
}
