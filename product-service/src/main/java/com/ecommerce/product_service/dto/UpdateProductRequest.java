package com.ecommerce.product_service.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpdateProductRequest(
        @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
        String name,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        @Positive(message = "Price must be greater than 0")
        BigDecimal price,

        UUID categoryId,

        List<@Size(max = 1000) String> imageUrls,

        String sku,

        Boolean active
) {
}
