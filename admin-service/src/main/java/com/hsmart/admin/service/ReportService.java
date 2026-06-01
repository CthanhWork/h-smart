package com.hsmart.admin.service;

import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.application.dto.ReportActionRequestDTO;
import com.hsmart.admin.application.dto.ReportRequestDTO;
import com.hsmart.admin.application.dto.ReportResponseDTO;
import org.springframework.data.domain.Pageable;

public interface ReportService {
    ReportResponseDTO submitReport(String reporterId, ReportRequestDTO request);
    PageResponseDTO<ReportResponseDTO> getPendingReports(Pageable pageable);
    ReportResponseDTO processReport(Long reportId, ReportActionRequestDTO request);
}
