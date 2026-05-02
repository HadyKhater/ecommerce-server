package com.ecommerce.order_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client for Cart Service using Eureka service discovery.
 * Uses lb://cart-service to load balance through Eureka registry.
 */
@FeignClient(name = "cart-service")
public interface CartClient {

    @GetMapping("/api/cart")
    CartResponse getCart(@RequestHeader("Authorization") String authHeader);

    @DeleteMapping("/api/cart")
    void clearCart(@RequestHeader("Authorization") String authHeader);

    // DTO Class
    record CartResponse(
        String userId,
        java.util.List<CartItemDTO> items,
        java.math.BigDecimal total
    ) {}

    record CartItemDTO(
        String productId,
        int quantity,
        java.math.BigDecimal price
    ) {}
}

