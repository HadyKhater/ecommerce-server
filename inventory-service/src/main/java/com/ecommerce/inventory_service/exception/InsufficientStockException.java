package com.ecommerce.inventory_service.exception;

import java.util.UUID;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(UUID productId, int requested, int available) {
        super(String.format("Insufficient stock for product %s. Requested: %d, Available: %d",
            productId, requested, available));
    }

    public InsufficientStockException(String message) {
        super(message);
    }
}
