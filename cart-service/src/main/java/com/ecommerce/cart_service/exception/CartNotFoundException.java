package com.ecommerce.cart_service.exception;

import java.util.UUID;

/**
 * Exception thrown when a cart cannot be found.
 * Can be thrown by cart ID or user email.
 */
public class CartNotFoundException extends RuntimeException {

    public CartNotFoundException(UUID id) {
        super("Cart not found with id: " + id);
    }

    public CartNotFoundException(String userEmail) {
        super("Cart not found for user: " + userEmail);
    }
}
