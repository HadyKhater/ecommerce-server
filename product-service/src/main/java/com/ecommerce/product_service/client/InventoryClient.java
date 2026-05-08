package com.ecommerce.product_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/**
 * Feign client for Inventory Service using Eureka service discovery.
 * Enables Product Service to initialize inventory when new products are created.
 */
@FeignClient(
    name = "inventory-service",
    configuration = FeignClientConfig.class
)
public interface InventoryClient {

    /**
     * Initialize inventory for a newly created product.
     * Called automatically when a product is created.
     */
    @PostMapping("/inventory/initialize/{productId}")
    void initializeInventory(@PathVariable UUID productId, @RequestBody InitializeRequest request);

    record InitializeRequest(int initialQuantity) {}
}
