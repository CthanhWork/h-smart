package com.hsmart.admin.presentation.controllers;

import com.hsmart.admin.application.dto.ApiResponse;
import com.hsmart.admin.application.dto.OverviewStatsResponseDTO;
import com.hsmart.admin.service.AdminAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminAnalyticsService adminAnalyticsService;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<OverviewStatsResponseDTO>> getOverviewStats() {
        OverviewStatsResponseDTO response = adminAnalyticsService.getOverviewStats();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Admin overview stats fetched successfully", response));
    }
}
