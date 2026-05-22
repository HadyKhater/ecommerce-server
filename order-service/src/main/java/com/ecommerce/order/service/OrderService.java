package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.OrderStatus;

import java.util.List;
import java.util.UUID;

public interface OrderService {
    OrderResponse createOrder(UUID userId, OrderRequest request);
    OrderResponse getOrder(UUID orderId);
    List<OrderResponse> getUserOrders(UUID userId);
    void updatePaymentStatus(UUID orderId, String status);
    OrderResponse cancelOrder(UUID orderId);
    List<OrderResponse> getAllOrders();
    OrderResponse updateOrderStatus(UUID orderId, OrderStatus status);
    
    // ==================== Two-Phase Order Creation Methods ====================
    
    /**
     * Phase 1: Create a temporary order reservation (awaiting payment)
     * This reserves stock but does not finalize the order.
     * The reservation expires after a configurable timeout if not confirmed.
     */
    OrderResponse createOrderReservation(UUID userId, OrderRequest request);
    
    /**
     * Phase 2: Confirm a reserved order after successful payment
     * This converts the reservation to a confirmed order.
     */
    OrderResponse confirmOrder(UUID orderId);
    
    /**
     * Cancel a reserved order (e.g., on payment failure)
     * This releases the reserved stock and marks the order as cancelled.
     */
    OrderResponse cancelReservation(UUID orderId);
}
