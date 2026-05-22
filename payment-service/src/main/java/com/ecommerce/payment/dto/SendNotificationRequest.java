package com.ecommerce.payment.dto;

import java.util.UUID;

public record SendNotificationRequest(
        UUID userId,
        String title,
        String message,
        String type
) {}
