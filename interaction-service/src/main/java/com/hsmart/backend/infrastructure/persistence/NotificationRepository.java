package com.hsmart.backend.infrastructure.persistence;

import com.hsmart.backend.domain.entities.Notification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findByUserIdOrderByTimestampDesc(String userId);
    Optional<Notification> findByIdAndUserId(String id, String userId);
}
