package com.ecommerce.cart_service.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO for cart item details.
 * Includes unit price snapshot and computed total.
 */
public record CartItemResponse(
        UUID productId,
        String name,
        BigDecimal unitPrice,
        String imageUrl,
        Integer quantity,
        BigDecimal totalPrice
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
