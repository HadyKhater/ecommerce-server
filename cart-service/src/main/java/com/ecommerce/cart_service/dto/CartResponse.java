package com.ecommerce.cart_service.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for cart details.
 * Contains cart metadata, items, and computed totals.
 */
public record CartResponse(
        UUID cartId,
        String userEmail,
        List<CartItemResponse> items,
        Integer totalItemsCount,
        BigDecimal grandTotal,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
