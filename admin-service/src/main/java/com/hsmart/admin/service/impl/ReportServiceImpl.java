package com.hsmart.admin.service.impl;

import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.application.dto.ReportActionRequestDTO;
import com.hsmart.admin.application.dto.ReportRequestDTO;
import com.hsmart.admin.application.dto.ReportResponseDTO;
import com.hsmart.admin.application.exceptions.MissingUserContextException;
import com.hsmart.admin.application.exceptions.ReportAlreadyProcessedException;
import com.hsmart.admin.application.exceptions.ReportNotFoundException;
import com.hsmart.admin.domain.entities.Report;
import com.hsmart.admin.domain.entities.ReportAction;
import com.hsmart.admin.domain.entities.ReportStatus;
import com.hsmart.admin.infrastructure.persistence.ReportRepository;
import com.hsmart.admin.service.ProductAdminClient;
import com.hsmart.admin.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ReportServiceImpl implements ReportService {

    private static final String HIDDEN_PRODUCT_STATUS = "HIDDEN";

    private final ReportRepository reportRepository;
    private final ProductAdminClient productAdminClient;

    @Override
    public ReportResponseDTO submitReport(String reporterId, ReportRequestDTO request) {
        String resolvedReporterId = requireReporterId(reporterId);
        Report report = Report.builder()
                .reporterId(resolvedReporterId)
                .productId(request.getProductId())
                .reason(request.getReason().trim())
                .status(ReportStatus.PENDING)
                .build();

        Report savedReport = reportRepository.save(report);
        log.info("Submitted report {} for product {} by user {}", savedReport.getId(), savedReport.getProductId(), resolvedReporterId);
        return toResponse(savedReport);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ReportResponseDTO> getPendingReports(Pageable pageable) {
        Page<ReportResponseDTO> reports = reportRepository.findAllByStatus(ReportStatus.PENDING, pageable)
                .map(this::toResponse);
        return PageResponseDTO.from(reports);
    }

    @Override
    public ReportResponseDTO processReport(Long reportId, ReportActionRequestDTO request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportNotFoundException(reportId));
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new ReportAlreadyProcessedException(reportId);
        }

        ReportAction action = request.getAction();
        if (action == ReportAction.HIDE_PRODUCT) {
            productAdminClient.updateModerationStatus(report.getProductId(), HIDDEN_PRODUCT_STATUS);
            report.setStatus(ReportStatus.RESOLVED);
        } else {
            report.setStatus(ReportStatus.DISMISSED);
        }

        Report savedReport = reportRepository.save(report);
        log.info("Processed report {} with action {} and status {}", savedReport.getId(), action, savedReport.getStatus());
        return toResponse(savedReport);
    }

    private String requireReporterId(String reporterId) {
        if (!StringUtils.hasText(reporterId)) {
            throw new MissingUserContextException();
        }
        return reporterId.trim();
    }

    private ReportResponseDTO toResponse(Report report) {
        return ReportResponseDTO.builder()
                .id(report.getId())
                .reporterId(report.getReporterId())
                .productId(report.getProductId())
                .reason(report.getReason())
                .status(report.getStatus())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
