package com.hsmart.admin.infrastructure.persistence;

import com.hsmart.admin.domain.entities.AdminNotification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminNotificationRepository extends JpaRepository<AdminNotification, Long> {
}
