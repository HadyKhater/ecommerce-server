package com.ecommerce.inventory_service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record StockMovementResponse(
    UUID id,
    UUID productId,
    String movementType,
    int quantity,
    String reason,
    LocalDateTime createdAt
) {}
