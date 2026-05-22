package com.ecommerce.order.controller;

import com.ecommerce.order.dto.OrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null || authentication.getPrincipal().equals("anonymousUser")) {
            return null; // or throw unauthorized
        }
        return (UUID) authentication.getPrincipal();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        UUID userId = getCurrentUserId();
        log.info("POST /orders - Create order for user: {}", userId);
        OrderResponse response = orderService.createOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId) {
        log.info("GET /orders/{} - Get order details", orderId);
        OrderResponse response = orderService.getOrder(orderId);
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                
        if (!isAdmin && !response.getUserId().equals(getCurrentUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<OrderResponse>> getUserOrders(@PathVariable UUID userId) {
        log.info("GET /orders/user/{} - Get user orders", userId);
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                
        if (!isAdmin && !userId.equals(getCurrentUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        List<OrderResponse> responses = orderService.getUserOrders(userId);
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{orderId}/payment-status")
    public ResponseEntity<Void> updatePaymentStatus(
            @PathVariable UUID orderId,
            @RequestParam String status) {
        log.info("PUT /orders/{}/payment-status?status={} - Internal update payment status", orderId, status);
        orderService.updatePaymentStatus(orderId, status);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{orderId}/cancel")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID orderId) {
        log.info("PUT /orders/{}/cancel - Cancel order", orderId);
        
        OrderResponse checkResponse = orderService.getOrder(orderId);
        if (!checkResponse.getUserId().equals(getCurrentUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        OrderResponse response = orderService.cancelOrder(orderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        log.info("GET /orders/admin/all - Get all orders");
        List<OrderResponse> responses = orderService.getAllOrders();
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/admin/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable UUID orderId,
            @RequestParam OrderStatus status) {
        log.info("PUT /orders/admin/{}/status?status={} - Update order status", orderId, status);
        OrderResponse response = orderService.updateOrderStatus(orderId, status);
        return ResponseEntity.ok(response);
    }

    // ==================== Two-Phase Order Creation Endpoints ====================

    /**
     * Phase 1: Create a temporary order reservation (awaiting payment)
     * This reserves stock but does not finalize the order.
     * The reservation expires after a configurable timeout if not confirmed.
     */
    @PostMapping("/reserve")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<OrderResponse> createOrderReservation(@Valid @RequestBody OrderRequest request) {
        UUID userId = getCurrentUserId();
        log.info("POST /orders/reserve - Create order reservation for user: {}", userId);
        OrderResponse response = orderService.createOrderReservation(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Phase 2: Confirm a reserved order after successful payment
     * This converts the reservation to a confirmed order.
     */
    @PutMapping("/{orderId}/confirm")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<OrderResponse> confirmOrder(@PathVariable UUID orderId) {
        log.info("PUT /orders/{}/confirm - Confirm reserved order", orderId);
        OrderResponse response = orderService.confirmOrder(orderId);
        return ResponseEntity.ok(response);
    }

    /**
     * Cancel a reserved order (e.g., on payment failure)
     * This releases the reserved stock and marks the order as cancelled.
     */
    @PutMapping("/{orderId}/cancel-reservation")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<OrderResponse> cancelReservation(@PathVariable UUID orderId) {
        log.info("PUT /orders/{}/cancel-reservation - Cancel order reservation", orderId);
        
        OrderResponse checkResponse = orderService.getOrder(orderId);
        if (!checkResponse.getUserId().equals(getCurrentUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        OrderResponse response = orderService.cancelReservation(orderId);
        return ResponseEntity.ok(response);
    }
}
