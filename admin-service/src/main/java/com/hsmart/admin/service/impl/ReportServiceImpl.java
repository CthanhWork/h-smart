package com.hsmart.admin.service.impl;

import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.application.dto.ReportActionRequestDTO;
import com.hsmart.admin.application.dto.ReportRequestDTO;
import com.hsmart.admin.application.dto.ReportResponseDTO;
import com.hsmart.admin.application.exceptions.DuplicatePendingReportException;
import com.hsmart.admin.application.exceptions.MissingUserContextException;
import com.hsmart.admin.application.exceptions.ReportAlreadyProcessedException;
import com.hsmart.admin.application.exceptions.ReportNotFoundException;
import com.hsmart.admin.domain.entities.AdminNotification;
import com.hsmart.admin.domain.entities.Report;
import com.hsmart.admin.domain.entities.ReportAction;
import com.hsmart.admin.domain.entities.ReportStatus;
import com.hsmart.admin.infrastructure.persistence.AdminNotificationRepository;
import com.hsmart.admin.infrastructure.persistence.ReportRepository;
import com.hsmart.admin.service.ProductAdminClient;
import com.hsmart.admin.service.ReportService;
import java.time.LocalDateTime;
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
    private static final String REPORT_NOTIFICATION_TYPE = "REPORT_PENDING";

    private final ReportRepository reportRepository;
    private final ProductAdminClient productAdminClient;
    private final AdminNotificationRepository adminNotificationRepository;

    @Override
    public ReportResponseDTO submitReport(String reporterId, ReportRequestDTO request) {
        String resolvedReporterId = requireReporterId(reporterId);
        if (reportRepository.existsByReporterIdAndProductIdAndStatus(
                resolvedReporterId,
                request.getProductId(),
                ReportStatus.PENDING
        )) {
            throw new DuplicatePendingReportException(request.getProductId());
        }

        Report report = Report.builder()
                .reporterId(resolvedReporterId)
                .productId(request.getProductId())
                .reason(request.getReason().trim())
                .status(ReportStatus.PENDING)
                .build();

        Report savedReport = reportRepository.save(report);
        saveAdminNotification(savedReport);
        log.info("Submitted report {} for product {} by user {}", savedReport.getId(), savedReport.getProductId(), resolvedReporterId);
        return toResponse(savedReport);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ReportResponseDTO> listReports(ReportStatus status, Pageable pageable) {
        Page<ReportResponseDTO> reports = (status == null
                ? reportRepository.findAll(pageable)
                : reportRepository.findAllByStatus(status, pageable))
                .map(this::toResponse);
        return PageResponseDTO.from(reports);
    }

    @Override
    public ReportResponseDTO processReport(Long reportId, ReportActionRequestDTO request, String adminUserId) {
        String resolvedAdminUserId = requireReporterId(adminUserId);
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportNotFoundException(reportId));
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new ReportAlreadyProcessedException(reportId);
        }

        ReportAction action = request.getAction();
        String resolutionReason = normalize(request.getResolutionReason());
        if (action == ReportAction.HIDE_PRODUCT) {
            productAdminClient.updateModerationStatus(report.getProductId(), HIDDEN_PRODUCT_STATUS);
            report.setStatus(ReportStatus.RESOLVED);
        } else {
            report.setStatus(ReportStatus.DISMISSED);
        }
        report.setProcessedAt(LocalDateTime.now());
        report.setProcessedBy(resolvedAdminUserId);
        report.setResolutionReason(resolutionReason);

        Report savedReport = reportRepository.save(report);
        adminNotificationRepository.markProcessedByReportIdAndType(
                reportId,
                REPORT_NOTIFICATION_TYPE,
                savedReport.getProcessedAt(),
                resolvedAdminUserId,
                resolutionReason
        );
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
                .processedAt(report.getProcessedAt())
                .processedBy(report.getProcessedBy())
                .resolutionReason(report.getResolutionReason())
                .build();
    }

    private void saveAdminNotification(Report report) {
        adminNotificationRepository.save(AdminNotification.builder()
                .productId(report.getProductId())
                .reportId(report.getId())
                .title("Báo cáo sản phẩm mới")
                .type(REPORT_NOTIFICATION_TYPE)
                .message("Sản phẩm " + report.getProductId() + " vừa bị báo cáo. Lý do: " + report.getReason())
                .build());
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
