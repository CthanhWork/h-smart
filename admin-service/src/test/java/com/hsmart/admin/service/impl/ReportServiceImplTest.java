package com.hsmart.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hsmart.admin.application.dto.PageResponseDTO;
import com.hsmart.admin.application.dto.ReportActionRequestDTO;
import com.hsmart.admin.application.dto.ReportRequestDTO;
import com.hsmart.admin.application.dto.ReportResponseDTO;
import com.hsmart.admin.application.exceptions.DuplicatePendingReportException;
import com.hsmart.admin.domain.entities.AdminNotification;
import com.hsmart.admin.application.exceptions.ReportAlreadyProcessedException;
import com.hsmart.admin.domain.entities.Report;
import com.hsmart.admin.domain.entities.ReportAction;
import com.hsmart.admin.domain.entities.ReportStatus;
import com.hsmart.admin.infrastructure.persistence.AdminNotificationRepository;
import com.hsmart.admin.infrastructure.persistence.ReportRepository;
import com.hsmart.admin.service.ProductAdminClient;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ProductAdminClient productAdminClient;

    @Mock
    private AdminNotificationRepository adminNotificationRepository;

    @Test
    void shouldSubmitPendingReportForAuthenticatedUser() {
        ReportServiceImpl service = new ReportServiceImpl(reportRepository, productAdminClient, adminNotificationRepository);
        when(reportRepository.save(org.mockito.ArgumentMatchers.any(Report.class)))
                .thenAnswer(invocation -> {
                    Report report = invocation.getArgument(0);
                    report.setId(10L);
                    report.setCreatedAt(LocalDateTime.now());
                    return report;
                });
        when(reportRepository.existsByReporterIdAndProductIdAndStatus("buyer-1", 20L, ReportStatus.PENDING))
                .thenReturn(false);

        ReportResponseDTO response = service.submitReport(" buyer-1 ", ReportRequestDTO.builder()
                .productId(20L)
                .reason(" Misleading description ")
                .build());

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getReporterId()).isEqualTo("buyer-1");
        assertThat(captor.getValue().getReason()).isEqualTo("Misleading description");
        assertThat(response.getStatus()).isEqualTo(ReportStatus.PENDING);
        verify(adminNotificationRepository).save(org.mockito.ArgumentMatchers.any(AdminNotification.class));
    }

    @Test
    void shouldRejectDuplicatePendingReportFromSameUserForSameProduct() {
        ReportServiceImpl service = new ReportServiceImpl(reportRepository, productAdminClient, adminNotificationRepository);
        when(reportRepository.existsByReporterIdAndProductIdAndStatus("buyer-1", 20L, ReportStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submitReport("buyer-1", ReportRequestDTO.builder()
                .productId(20L)
                .reason("Misleading description")
                .build()))
                .isInstanceOf(DuplicatePendingReportException.class);

        verify(reportRepository, never()).save(org.mockito.ArgumentMatchers.any(Report.class));
    }

    @Test
    void shouldReturnOnlyFilteredReportsWithPagination() {
        ReportServiceImpl service = new ReportServiceImpl(reportRepository, productAdminClient, adminNotificationRepository);
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Report report = report(11L, ReportStatus.PENDING);
        when(reportRepository.findAllByStatus(ReportStatus.PENDING, pageable))
                .thenReturn(new PageImpl<>(List.of(report), pageable, 1));

        PageResponseDTO<ReportResponseDTO> response = service.listReports(ReportStatus.PENDING, pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getId()).isEqualTo(11L);
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldDismissPendingReportWithoutCallingProductService() {
        ReportServiceImpl service = new ReportServiceImpl(reportRepository, productAdminClient, adminNotificationRepository);
        Report report = report(12L, ReportStatus.PENDING);
        when(reportRepository.findById(12L)).thenReturn(Optional.of(report));
        when(reportRepository.save(report)).thenReturn(report);

        ReportResponseDTO response = service.processReport(
                12L,
                new ReportActionRequestDTO(ReportAction.DISMISS, "Không đủ bằng chứng"),
                "admin-1"
        );

        assertThat(response.getStatus()).isEqualTo(ReportStatus.DISMISSED);
        assertThat(response.getProcessedBy()).isEqualTo("admin-1");
        assertThat(response.getResolutionReason()).isEqualTo("Không đủ bằng chứng");
        verify(productAdminClient, never()).updateModerationStatus(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
        verify(adminNotificationRepository).markProcessedByReportIdAndType(
                org.mockito.Mockito.eq(12L),
                org.mockito.Mockito.eq("REPORT_PENDING"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.Mockito.eq("admin-1"),
                org.mockito.Mockito.eq("Không đủ bằng chứng")
        );
    }

    @Test
    void shouldHideProductAndResolvePendingReport() {
        ReportServiceImpl service = new ReportServiceImpl(reportRepository, productAdminClient, adminNotificationRepository);
        Report report = report(13L, ReportStatus.PENDING);
        when(reportRepository.findById(13L)).thenReturn(Optional.of(report));
        when(reportRepository.save(report)).thenReturn(report);

        ReportResponseDTO response = service.processReport(
                13L,
                new ReportActionRequestDTO(ReportAction.HIDE_PRODUCT, "Nội dung gây hiểu lầm"),
                "admin-2"
        );

        verify(productAdminClient).updateModerationStatus(99L, "HIDDEN");
        assertThat(response.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(response.getProcessedBy()).isEqualTo("admin-2");
    }

    @Test
    void shouldRejectReportThatWasAlreadyProcessed() {
        ReportServiceImpl service = new ReportServiceImpl(reportRepository, productAdminClient, adminNotificationRepository);
        when(reportRepository.findById(14L)).thenReturn(Optional.of(report(14L, ReportStatus.RESOLVED)));

        assertThatThrownBy(() -> service.processReport(
                14L,
                new ReportActionRequestDTO(ReportAction.DISMISS, null),
                "admin-1"
        ))
                .isInstanceOf(ReportAlreadyProcessedException.class);
    }

    private Report report(Long id, ReportStatus status) {
        return Report.builder()
                .id(id)
                .reporterId("buyer-1")
                .productId(99L)
                .reason("Misleading description")
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
