package com.ecommerce.cart_service.exception;

import java.util.UUID;

public class CartItemNotFoundException extends RuntimeException {

    public CartItemNotFoundException(UUID productId) {
        super("Cart item not found for product id: " + productId);
    }

    public CartItemNotFoundException(String message) {
        super(message);
    }
}
