package com.ecommerce.inventory_service.service;

import com.ecommerce.inventory_service.dto.*;
import com.ecommerce.inventory_service.entity.Inventory;
import com.ecommerce.inventory_service.entity.StockMovement;
import com.ecommerce.inventory_service.enums.MovementType;
import com.ecommerce.inventory_service.exception.InsufficientStockException;
import com.ecommerce.inventory_service.exception.InventoryNotFoundException;
import com.ecommerce.inventory_service.repository.InventoryRepository;
import com.ecommerce.inventory_service.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService implements IInventoryService {

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;

    @Override
    public InventoryResponse getByProductId(UUID productId) {
        log.debug("Getting inventory for product: {}", productId);

        Inventory inventory = inventoryRepository.findByProductId(productId)
            .orElseThrow(() -> new InventoryNotFoundException(productId));

        return mapToInventoryResponse(inventory);
    }

    @Override
    public List<InventoryResponse> getAllInventory() {
        log.debug("Getting all inventory");

        return inventoryRepository.findAll().stream()
            .map(this::mapToInventoryResponse)
            .toList();
    }

    @Override
    public List<InventoryResponse> getLowStockItems() {
        log.debug("Getting low stock items");

        return inventoryRepository.findAll().stream()
            .filter(Inventory::isLowStock)
            .map(this::mapToInventoryResponse)
            .toList();
    }

    @Override
    @Transactional
    public InventoryResponse restockProduct(RestockRequest request) {
        log.info("Restocking product: {} with quantity: {}", request.productId(), request.quantity());

        Optional<Inventory> existingInventory = inventoryRepository.findByProductId(request.productId());

        Inventory inventory;
        if (existingInventory.isPresent()) {
            inventory = existingInventory.get();
            inventory.setQuantityAvailable(inventory.getQuantityAvailable() + request.quantity());
        } else {
            inventory = new Inventory();
            inventory.setProductId(request.productId());
            inventory.setQuantityAvailable(request.quantity());
            inventory.setQuantityReserved(0);
            inventory.setLowStockThreshold(10);
        }

        Inventory savedInventory = inventoryRepository.save(inventory);

        saveStockMovement(request.productId(), MovementType.RESTOCK, request.quantity(), request.reason());

        log.info("Restocked product: {}. New available quantity: {}",
            request.productId(), savedInventory.getQuantityAvailable());

        return mapToInventoryResponse(savedInventory);
    }

    @Override
    @Transactional
    public InventoryResponse adjustStock(UUID productId, AdjustStockRequest request) {
        log.info("Adjusting stock for product: {}", productId);

        Inventory inventory = inventoryRepository.findByProductId(productId)
            .orElseThrow(() -> new InventoryNotFoundException(productId));

        inventory.setQuantityAvailable(request.quantityAvailable());
        inventory.setQuantityReserved(request.quantityReserved());
        inventory.setLowStockThreshold(request.lowStockThreshold());

        Inventory savedInventory = inventoryRepository.save(inventory);

        saveStockMovement(productId, MovementType.ADJUSTMENT,
            request.quantityAvailable() - inventory.getQuantityAvailable(), request.reason());

        log.info("Adjusted stock for product: {}", productId);

        return mapToInventoryResponse(savedInventory);
    }

    @Override
    @Transactional
    public InventoryResponse reserveStock(ReserveStockRequest request) {
        log.info("Reserving stock for product: {} with quantity: {}",
            request.productId(), request.quantity());

        Inventory inventory = inventoryRepository.findByProductId(request.productId())
            .orElseThrow(() -> new InventoryNotFoundException(request.productId()));

        if (inventory.getQuantityAvailable() < request.quantity()) {
            throw new InsufficientStockException(request.productId(), request.quantity(),
                inventory.getQuantityAvailable());
        }

        inventory.setQuantityAvailable(inventory.getQuantityAvailable() - request.quantity());
        inventory.setQuantityReserved(inventory.getQuantityReserved() + request.quantity());

        Inventory savedInventory = inventoryRepository.save(inventory);

        saveStockMovement(request.productId(), MovementType.RESERVATION, request.quantity(), null);

        log.info("Reserved {} units for product: {}. Available: {}, Reserved: {}",
            request.quantity(), request.productId(),
            savedInventory.getQuantityAvailable(), savedInventory.getQuantityReserved());

        return mapToInventoryResponse(savedInventory);
    }

    @Override
    @Transactional
    public InventoryResponse releaseStock(ReserveStockRequest request) {
        log.info("Releasing stock for product: {} with quantity: {}",
            request.productId(), request.quantity());

        Inventory inventory = inventoryRepository.findByProductId(request.productId())
            .orElseThrow(() -> new InventoryNotFoundException(request.productId()));

        int releaseQuantity = Math.min(request.quantity(), inventory.getQuantityReserved());

        inventory.setQuantityReserved(inventory.getQuantityReserved() - releaseQuantity);
        inventory.setQuantityAvailable(inventory.getQuantityAvailable() + releaseQuantity);

        Inventory savedInventory = inventoryRepository.save(inventory);

        saveStockMovement(request.productId(), MovementType.RELEASE, releaseQuantity, null);

        log.info("Released {} units for product: {}. Available: {}, Reserved: {}",
            releaseQuantity, request.productId(),
            savedInventory.getQuantityAvailable(), savedInventory.getQuantityReserved());

        return mapToInventoryResponse(savedInventory);
    }

    @Override
    @Transactional
    public void initializeInventory(UUID productId, int initialQuantity) {
        log.info("Initializing inventory for product: {} with quantity: {}", productId, initialQuantity);

        if (inventoryRepository.existsByProductId(productId)) {
            log.warn("Inventory already exists for product: {}", productId);
            return;
        }

        Inventory inventory = new Inventory();
        inventory.setProductId(productId);
        inventory.setQuantityAvailable(initialQuantity);
        inventory.setQuantityReserved(0);
        inventory.setLowStockThreshold(10);

        inventoryRepository.save(inventory);

        saveStockMovement(productId, MovementType.RESTOCK, initialQuantity, "Initial stock");

        log.info("Initialized inventory for product: {}", productId);
    }

    @Override
    public List<StockMovementResponse> getMovementHistory(UUID productId) {
        log.debug("Getting movement history for product: {}", productId);

        return stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
            .map(this::mapToStockMovementResponse)
            .toList();
    }

    @Override
    public boolean isInStock(UUID productId, int requiredQuantity) {
        log.debug("Checking stock for product: {} with required quantity: {}",
            productId, requiredQuantity);

        Optional<Inventory> inventory = inventoryRepository.findByProductId(productId);

        if (inventory.isEmpty()) {
            return false;
        }

        return inventory.get().getQuantityAvailable() >= requiredQuantity;
    }

    @Override
    public long getOutOfStockCount() {
        log.debug("Getting out of stock count");
        return inventoryRepository.countByQuantityAvailable(0);
    }

    private void saveStockMovement(UUID productId, MovementType type, int quantity, String reason) {
        StockMovement movement = new StockMovement();
        movement.setProductId(productId);
        movement.setMovementType(type.name());
        movement.setQuantity(quantity);
        movement.setReason(reason);

        stockMovementRepository.save(movement);
    }

    private InventoryResponse mapToInventoryResponse(Inventory inventory) {
        return new InventoryResponse(
            inventory.getId(),
            inventory.getProductId(),
            inventory.getQuantityAvailable(),
            inventory.getQuantityReserved(),
            inventory.getLowStockThreshold(),
            inventory.isLowStock(),
            inventory.isOutOfStock(),
            inventory.getCreatedAt(),
            inventory.getUpdatedAt()
        );
    }

    private StockMovementResponse mapToStockMovementResponse(StockMovement movement) {
        return new StockMovementResponse(
            movement.getId(),
            movement.getProductId(),
            movement.getMovementType(),
            movement.getQuantity(),
            movement.getReason(),
            movement.getCreatedAt()
        );
    }
}
