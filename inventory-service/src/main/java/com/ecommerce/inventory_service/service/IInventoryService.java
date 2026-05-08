package com.ecommerce.inventory_service.service;

import com.ecommerce.inventory_service.dto.*;

import java.util.List;
import java.util.UUID;

public interface IInventoryService {

    InventoryResponse getByProductId(UUID productId);

    List<InventoryResponse> getAllInventory();

    List<InventoryResponse> getLowStockItems();

    InventoryResponse restockProduct(RestockRequest request);

    InventoryResponse adjustStock(UUID productId, AdjustStockRequest request);

    InventoryResponse reserveStock(ReserveStockRequest request);

    InventoryResponse releaseStock(ReserveStockRequest request);

    void initializeInventory(UUID productId, int initialQuantity);

    List<StockMovementResponse> getMovementHistory(UUID productId);

    boolean isInStock(UUID productId, int requiredQuantity);
}
