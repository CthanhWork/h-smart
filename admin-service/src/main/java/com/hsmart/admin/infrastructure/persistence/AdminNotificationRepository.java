package com.hsmart.admin.infrastructure.persistence;

import com.hsmart.admin.domain.entities.AdminNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminNotificationRepository extends JpaRepository<AdminNotification, Long> {

    @Modifying
    @Query("""
            update AdminNotification notification
            set notification.processed = true
            where notification.productId = :productId
              and notification.type = :type
              and notification.processed = false
            """)
    int markProcessedByProductIdAndType(
            @Param("productId") Long productId,
            @Param("type") String type
    );
}
