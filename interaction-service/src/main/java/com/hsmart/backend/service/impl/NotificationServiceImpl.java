package com.hsmart.backend.service.impl;

import com.hsmart.backend.application.dto.NotificationRequestDTO;
import com.hsmart.backend.application.dto.NotificationCountResponseDTO;
import com.hsmart.backend.application.dto.NotificationResponseDTO;
import com.hsmart.backend.application.dto.PageResponseDTO;
import com.hsmart.backend.application.mapper.NotificationMapper;
import com.hsmart.backend.domain.entities.Notification;
import com.hsmart.backend.infrastructure.messaging.WebSocketMessagePublisher;
import com.hsmart.backend.infrastructure.persistence.NotificationRepository;
import com.hsmart.backend.service.NotificationService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final WebSocketMessagePublisher messagePublisher;
    private final MongoTemplate mongoTemplate;

    @Override
    public NotificationResponseDTO createNotification(NotificationRequestDTO request) {
        Notification notification = Notification.builder()
                .userId(request.getUserId())
                .title(normalize(request.getTitle()))
                .type(request.getType())
                .message(request.getMessage())
                .productId(request.getProductId())
                .orderId(request.getOrderId())
                .offerId(request.getOfferId())
                .senderId(normalize(request.getSenderId()))
                .read(false)
                .timestamp(Instant.now())
                .build();

        Notification saved = notificationRepository.save(notification);
        NotificationResponseDTO response = toResponse(saved, unreadCountForUser(saved.getUserId()));
        messagePublisher.sendNotification(response.getUserId(), response);
        log.info("Created notification {} for user {}", response.getId(), response.getUserId());
        return response;
    }

    @Override
    public NotificationResponseDTO createChatNotification(String senderId, String receiverId, Long productId) {
        String message = String.format("Bạn có tin nhắn mới từ %s về sản phẩm %d.", senderId, productId);
        NotificationRequestDTO request = NotificationRequestDTO.builder()
                .userId(receiverId)
                .title("Tin nhắn mới")
                .type("CHAT")
                .message(message)
                .productId(productId)
                .senderId(senderId)
                .build();
        return createNotification(request);
    }

    @Override
    public List<NotificationResponseDTO> getNotificationsForCurrentUser(String currentUserId) {
        long unreadCount = unreadCountForUser(currentUserId);
        return notificationRepository.findByUserIdOrderByTimestampDesc(currentUserId).stream()
                .map(notification -> toResponse(notification, unreadCount))
                .toList();
    }

    @Override
    public PageResponseDTO<NotificationResponseDTO> getNotificationsPage(
            String currentUserId,
            Boolean read,
            String type,
            Pageable pageable
    ) {
        Pageable normalizedPageable = pageable == null || pageable.isUnpaged()
                ? PageRequest.of(0, 20)
                : pageable;
        Query query = buildUserNotificationQuery(currentUserId, read, type)
                .with(normalizedPageable)
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));

        long unreadCount = unreadCountForUser(currentUserId);
        List<NotificationResponseDTO> content = mongoTemplate.find(query, Notification.class).stream()
                .map(notification -> toResponse(notification, unreadCount))
                .toList();
        long total = mongoTemplate.count(buildUserNotificationQuery(currentUserId, read, type), Notification.class);
        Page<NotificationResponseDTO> page = new PageImpl<>(content, normalizedPageable, total);
        return PageResponseDTO.from(page);
    }

    @Override
    public NotificationResponseDTO markNotificationAsRead(String currentUserId, String notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification = notificationRepository.save(notification);
        }
        return toResponse(notification, unreadCountForUser(currentUserId));
    }

    @Override
    public NotificationCountResponseDTO markAllNotificationsAsRead(String currentUserId) {
        Query query = new Query(Criteria.where("userId").is(currentUserId).and("read").is(false));
        mongoTemplate.updateMulti(query, Update.update("read", true), Notification.class);
        return getUnreadCount(currentUserId);
    }

    @Override
    public NotificationCountResponseDTO getUnreadCount(String currentUserId) {
        return NotificationCountResponseDTO.builder()
                .unreadCount(unreadCountForUser(currentUserId))
                .build();
    }

    private long unreadCountForUser(String currentUserId) {
        long unreadCount = mongoTemplate.count(
                new Query(Criteria.where("userId").is(currentUserId).and("read").is(false)),
                Notification.class
        );
        return unreadCount;
    }

    private Query buildUserNotificationQuery(String currentUserId, Boolean read, String type) {
        Criteria criteria = Criteria.where("userId").is(currentUserId);
        if (read != null) {
            criteria = criteria.and("read").is(read);
        }
        if (StringUtils.hasText(type)) {
            criteria = criteria.and("type").is(type.trim());
        }
        return new Query(criteria);
    }

    private NotificationResponseDTO toResponse(Notification notification, long unreadCount) {
        NotificationResponseDTO response = notificationMapper.toResponse(notification);
        response.setUnreadCount(unreadCount);
        return response;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
