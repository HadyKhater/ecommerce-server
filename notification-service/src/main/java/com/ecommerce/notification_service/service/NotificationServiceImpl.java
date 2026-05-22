package com.ecommerce.notification_service.service;

import com.ecommerce.notification_service.domain.Notification;
import com.ecommerce.notification_service.domain.NotificationType;
import com.ecommerce.notification_service.dto.NotificationResponse;
import com.ecommerce.notification_service.dto.SendNotificationRequest;
import com.ecommerce.notification_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements INotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public NotificationResponse sendNotification(SendNotificationRequest request) {
        log.info("Sending notification of type {} to user {}", request.type(), request.userId());

        NotificationType type;
        try {
            type = NotificationType.valueOf(request.type().toUpperCase());
        } catch (Exception e) {
            log.warn("Invalid notification type: {}, falling back to SYSTEM", request.type());
            type = NotificationType.SYSTEM;
        }

        Notification notification = Notification.builder()
                .userId(request.userId())
                .title(request.title())
                .message(request.message())
                .type(type)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        NotificationResponse response = mapToResponse(saved);

        // Push via WebSocket after successful save
        try {
            String destination = "/topic/notifications/" + request.userId();
            log.info("Pushing WebSocket event to {}", destination);
            messagingTemplate.convertAndSend(destination, response);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification to user {}: {}", request.userId(), e.getMessage());
        }

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsForUser(UUID userId) {
        log.info("Fetching top 20 notifications for user {}", userId);
        return notificationRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(UUID notificationId) {
        log.info("Marking notification {} as read", notificationId);
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));

        notification.setRead(true);
        Notification saved = notificationRepository.save(notification);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        log.info("Getting unread count for user {}", userId);
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getUserId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getType().name(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
