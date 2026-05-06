package com.ecommerce.product_service.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductDTO(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        UUID categoryId,
        String categoryName,
        List<String> imageUrls,
        String sku,
        Integer stockQuantity,
        Boolean active,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
