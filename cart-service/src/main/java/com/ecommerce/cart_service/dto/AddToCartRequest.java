package com.ecommerce.cart_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for adding an item to cart.
 * Used in POST /cart/items endpoint.
 */
public record AddToCartRequest(
        @NotNull(message = "Product ID is required")
        UUID productId,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity,

        /**
         * Optional unit price. If not provided, will be fetched from product-service.
         * This allows clients to pass a price they saw on the product page.
         */
        BigDecimal unitPrice
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
