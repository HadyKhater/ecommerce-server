package com.ecommerce.cart_service.repository;

import com.ecommerce.cart_service.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for CartItem entity operations.
 * Provides lookup by cart ID and product ID combination.
 */
@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    /**
     * Find cart item by cart ID and product ID.
     * Used to check if product already exists in cart.
     */
    Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId);

    /**
     * Delete all items for a specific cart.
     * Used when clearing cart.
     */
    void deleteAllByCartId(UUID cartId);
}
