package com.ecommerce.cart_service.service;

import com.ecommerce.cart_service.dto.AddToCartRequest;
import com.ecommerce.cart_service.dto.CartResponse;
import com.ecommerce.cart_service.dto.UpdateItemRequest;

import java.util.UUID;

/**
 * Cart service interface defining all cart operations.
 * All methods use user email (from X-User-Email header) for user identification.
 */
public interface ICartService {

    /**
     * Get current user's cart with items and computed total.
     */
    CartResponse getCart(String userEmail);

    /**
     * Add item to cart. Validates product exists via product-service.
     * Captures unit price snapshot at time of adding.
     */
    CartResponse addToCart(String userEmail, AddToCartRequest request);

    /**
     * Update item quantity in cart.
     */
    CartResponse updateQuantity(String userEmail, UUID productId, UpdateItemRequest request);

    /**
     * Remove single item from cart.
     */
    CartResponse removeFromCart(String userEmail, UUID productId);

    /**
     * Clear entire cart for user.
     */
    void clearCart(String userEmail);
}
