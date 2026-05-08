package com.ecommerce.inventory_service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record InventoryResponse(
    UUID id,
    UUID productId,
    int quantityAvailable,
    int quantityReserved,
    int lowStockThreshold,
    boolean isLowStock,
    boolean isOutOfStock,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
