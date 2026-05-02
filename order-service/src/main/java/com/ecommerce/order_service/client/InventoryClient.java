package com.ecommerce.order_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for Inventory Service using Eureka service discovery.
 * This ensures requests are automatically routed through the api-gateway
 * via lb://inventory-service load balancer.
 */
@FeignClient(name = "inventory-service")
public interface InventoryClient {

    @PostMapping("/api/inventory/reserve")
    InventoryReservationResponse reserve(@RequestBody InventoryReservationRequest request);

    @PostMapping("/api/inventory/confirm")
    void confirmReservation(@RequestBody ConfirmReservationRequest request);

    @PostMapping("/api/inventory/release")
    void releaseReservation(@RequestBody ReleaseReservationRequest request);

    // DTO Classes
    record InventoryReservationRequest(
        String productId,
        int quantity,
        String orderId
    ) {}

    record InventoryReservationResponse(
        String reservationId,
        String status
    ) {}

    record ConfirmReservationRequest(
        String reservationId
    ) {}

    record ReleaseReservationRequest(
        String reservationId
    ) {}
}

