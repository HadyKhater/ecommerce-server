package com.ecommerce.product_service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record BannerResponse(
        UUID id,
        String title,
        String subtitle,
        String imageUrl,
        String linkUrl,
        boolean active,
        int displayOrder,
        LocalDateTime createdAt
) {}
