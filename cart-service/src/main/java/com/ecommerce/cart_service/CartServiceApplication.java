package com.ecommerce.cart_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Cart Service - Microservice for shopping cart operations.
 *
 * Features:
 * - User cart management (identified by X-User-Email header)
 * - Cart items with unit price snapshots
 * - Optimistic locking for concurrent modifications
 * - Caffeine caching for performance
 * - Structured logging with correlation IDs
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableCaching
public class CartServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartServiceApplication.class, args);
    }
}
