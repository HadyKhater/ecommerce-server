package com.ecommerce.cart_service.repository;

import com.ecommerce.cart_service.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Cart entity operations.
 * Provides lookup by user email.
 */
@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {

    /**
     * Find cart by user email.
     * Each user has exactly one cart.
     */
    Optional<Cart> findByUserEmail(String userEmail);

    /**
     * Check if cart exists for user email.
     */
    boolean existsByUserEmail(String userEmail);
}
