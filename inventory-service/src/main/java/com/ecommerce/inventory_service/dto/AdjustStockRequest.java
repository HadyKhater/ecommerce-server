package com.ecommerce.inventory_service.dto;

import jakarta.validation.constraints.Min;

public record AdjustStockRequest(
    @Min(value = 0, message = "Quantity available must be at least 0")
    int quantityAvailable,

    @Min(value = 0, message = "Quantity reserved must be at least 0")
    int quantityReserved,

    @Min(value = 1, message = "Low stock threshold must be at least 1")
    int lowStockThreshold,

    String reason
) {}
