package com.ecommerce.notification_service.dto;

import java.util.UUID;

public record SendNotificationRequest(
        UUID userId,
        String title,
        String message,
        String type
) {}
