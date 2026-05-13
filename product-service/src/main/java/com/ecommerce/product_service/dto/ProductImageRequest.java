package com.ecommerce.product_service.dto;

import jakarta.validation.constraints.NotBlank;

public record ProductImageRequest(
        @NotBlank(message = "Image URL is required")
        String imageUrl,
        int displayOrder,
        boolean isPrimary
) {
}
