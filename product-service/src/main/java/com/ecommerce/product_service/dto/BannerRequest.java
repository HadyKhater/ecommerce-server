package com.ecommerce.product_service.dto;

import jakarta.validation.constraints.NotBlank;

public record BannerRequest(
        @NotBlank(message = "Title is required")
        String title,
        String subtitle,
        @NotBlank(message = "Image URL is required")
        String imageUrl,
        String linkUrl,
        boolean active,
        int displayOrder
) {}
