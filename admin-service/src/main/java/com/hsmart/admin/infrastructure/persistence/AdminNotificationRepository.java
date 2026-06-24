package com.hsmart.admin.infrastructure.persistence;

import com.hsmart.admin.domain.entities.AdminNotification;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminNotificationRepository extends JpaRepository<AdminNotification, Long> {

    Page<AdminNotification> findAllByProcessed(boolean processed, Pageable pageable);

    long countByProcessedFalse();

    @Modifying
    @Query("""
            update AdminNotification notification
            set notification.processed = true,
                notification.processedAt = :processedAt,
                notification.processedBy = :processedBy,
                notification.resolutionReason = :resolutionReason
            where notification.productId = :productId
              and notification.type = :type
              and notification.processed = false
            """)
    int markProcessedByProductIdAndType(
            @Param("productId") Long productId,
            @Param("type") String type,
            @Param("processedAt") LocalDateTime processedAt,
            @Param("processedBy") String processedBy,
            @Param("resolutionReason") String resolutionReason
    );

    @Modifying
    @Query("""
            update AdminNotification notification
            set notification.processed = true,
                notification.processedAt = :processedAt,
                notification.processedBy = :processedBy,
                notification.resolutionReason = :resolutionReason
            where notification.reportId = :reportId
              and notification.type = :type
              and notification.processed = false
            """)
    int markProcessedByReportIdAndType(
            @Param("reportId") Long reportId,
            @Param("type") String type,
            @Param("processedAt") LocalDateTime processedAt,
            @Param("processedBy") String processedBy,
            @Param("resolutionReason") String resolutionReason
    );
}
