package com.ecommerce.product_service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProductImageResponse(
        UUID id,
        String imageUrl,
        int displayOrder,
        boolean isPrimary,
        LocalDateTime createdAt
) {
}
