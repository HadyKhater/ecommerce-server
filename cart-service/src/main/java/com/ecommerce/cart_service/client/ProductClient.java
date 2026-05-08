package com.ecommerce.cart_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.UUID;

@FeignClient(
        name = "product-service",
        configuration = FeignClientConfig.class
)
public interface ProductClient {

    @GetMapping("/products/{id}")
    ProductDTO getProduct(@PathVariable("id") UUID id);

    record ProductDTO(
            UUID id,
            String name,
            String description,
            BigDecimal price,
            UUID categoryId,
            String categoryName,
            java.util.List<String> imageUrls,
            String sku,
            Boolean active,
            java.time.Instant createdAt,
            java.time.Instant updatedAt
    ) {}
}
