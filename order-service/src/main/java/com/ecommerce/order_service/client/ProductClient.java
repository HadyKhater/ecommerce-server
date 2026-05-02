package com.ecommerce.order_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for Product Service using Eureka service discovery.
 * Uses lb://product-service to load balance through Eureka registry.
 */
@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/api/products/{id}")
    ProductResponse getProduct(@PathVariable("id") String productId);

    // DTO Class
    record ProductResponse(
        String id,
        String name,
        String description,
        java.math.BigDecimal price,
        String category,
        String imageUrl
    ) {}
}

