package com.hsmart.admin.presentation.controllers;

import com.hsmart.admin.application.dto.ApiResponse;
import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.application.dto.ReportActionRequestDTO;
import com.hsmart.admin.application.dto.ReportResponseDTO;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponseDTO<ReportResponseDTO>>> getPendingReports(
            @ParameterObject
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "Pending reports fetched successfully",
                reportService.getPendingReports(pageable)
        ));
    }

    @PostMapping("/{id}/action")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> processReport(
            @PathVariable Long id,
            @Valid @RequestBody ReportActionRequestDTO request
    ) {
        ReportResponseDTO response = reportService.processReport(id, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Report processed successfully", response));
    }
}
