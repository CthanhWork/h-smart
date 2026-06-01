package com.hsmart.admin.infrastructure.persistence;

import com.hsmart.admin.domain.entities.Report;
import com.hsmart.admin.domain.entities.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {
    Page<Report> findAllByStatus(ReportStatus status, Pageable pageable);
}
