package com.ecommerce.inventory_service.controller;

import com.ecommerce.inventory_service.dto.*;
import com.ecommerce.inventory_service.service.IInventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

record InitializeRequest(int initialQuantity) {}

@Slf4j
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final IInventoryService inventoryService;

    @GetMapping
    public ResponseEntity<List<InventoryResponse>> getAllInventory() {
        log.debug("GET /inventory - Getting all inventory (admin only)");

        List<InventoryResponse> inventory = inventoryService.getAllInventory();
        return ResponseEntity.ok(inventory);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<InventoryResponse> getInventoryByProductId(
            @PathVariable UUID productId) {
        log.debug("GET /inventory/{} - Getting inventory for product", productId);

        InventoryResponse inventory = inventoryService.getByProductId(productId);
        return ResponseEntity.ok(inventory);
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<InventoryResponse>> getLowStockItems() {
        log.debug("GET /inventory/low-stock - Getting low stock items (admin only)");

        List<InventoryResponse> items = inventoryService.getLowStockItems();
        return ResponseEntity.ok(items);
    }

    @GetMapping("/out-of-stock/count")
    public ResponseEntity<Map<String, Long>> getOutOfStockCount() {
        log.debug("GET /inventory/out-of-stock/count - Getting out of stock count");
        long count = inventoryService.getOutOfStockCount();
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/{productId}/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StockMovementResponse>> getMovementHistory(
            @PathVariable UUID productId) {
        log.debug("GET /inventory/{}/history - Getting movement history (admin only)", productId);

        List<StockMovementResponse> history = inventoryService.getMovementHistory(productId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{productId}/check")
    public ResponseEntity<Map<String, Object>> checkStockAvailability(
            @PathVariable UUID productId,
            @RequestParam int quantity) {
        log.debug("GET /inventory/{}/check?quantity={} - Checking stock availability", productId, quantity);

        boolean available = inventoryService.isInStock(productId, quantity);
        InventoryResponse inventory = inventoryService.getByProductId(productId);

        Map<String, Object> response = Map.of(
            "available", available,
            "quantityAvailable", inventory.quantityAvailable()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/restock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InventoryResponse> restockProduct(
            @Valid @RequestBody RestockRequest request) {
        log.info("POST /inventory/restock - Restocking product: {} with quantity: {}",
            request.productId(), request.quantity());

        InventoryResponse response = inventoryService.restockProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{productId}/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InventoryResponse> adjustStock(
            @PathVariable UUID productId,
            @Valid @RequestBody AdjustStockRequest request) {
        log.info("PUT /inventory/{}/adjust - Adjusting stock", productId);

        InventoryResponse response = inventoryService.adjustStock(productId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reserve")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<InventoryResponse> reserveStock(
            @Valid @RequestBody ReserveStockRequest request) {
        log.info("POST /inventory/reserve - Reserving stock for product: {} with quantity: {}",
            request.productId(), request.quantity());

        InventoryResponse response = inventoryService.reserveStock(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/release")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<InventoryResponse> releaseStock(
            @Valid @RequestBody ReserveStockRequest request) {
        log.info("POST /inventory/release - Releasing stock for product: {} with quantity: {}",
            request.productId(), request.quantity());

        InventoryResponse response = inventoryService.releaseStock(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/initialize/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> initializeInventory(
            @PathVariable UUID productId,
            @RequestBody InitializeRequest request) {
        log.info("POST /inventory/initialize/{} - Initializing inventory with quantity: {}",
            productId, request.initialQuantity());

        inventoryService.initializeInventory(productId, request.initialQuantity());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
