package com.ecommerce.notification_service.service;

import com.ecommerce.notification_service.dto.NotificationResponse;
import com.ecommerce.notification_service.dto.SendNotificationRequest;

import java.util.List;
import java.util.UUID;

public interface INotificationService {
    NotificationResponse sendNotification(SendNotificationRequest request);

    List<NotificationResponse> getNotificationsForUser(UUID userId);

    NotificationResponse markAsRead(UUID notificationId);

    long getUnreadCount(UUID userId);
}
