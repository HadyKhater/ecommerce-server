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
}
