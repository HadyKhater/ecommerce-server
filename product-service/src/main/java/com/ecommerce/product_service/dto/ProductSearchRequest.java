package com.ecommerce.product_service.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSearchRequest(
        String keyword,
        UUID categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        int page,
        int size,
        Boolean active
) {
    public ProductSearchRequest {
        if (page < 0) {
            page = 0;
        }
        if (size < 1) {
            size = 25;
        }
        if (size > 100) {
            size = 100;
        }
    }

    public static ProductSearchRequest defaultRequest() {
        return new ProductSearchRequest(null, null, null, null, 0, 25, null);
    }
}
