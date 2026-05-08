package com.ecommerce.cart_service.dto;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * Request DTO for updating cart item quantity.
 * Used in PUT /cart/items/{productId} endpoint.
 * Quantity of 0 or less will remove the item from cart.
 */
public record UpdateItemRequest(
        @NotNull(message = "Quantity is required")
        Integer quantity
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
