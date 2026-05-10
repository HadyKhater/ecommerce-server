package com.ecommerce.order.service;

import com.ecommerce.order.client.InventoryClient;
import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.dto.*;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.exception.InsufficientStockException;
import com.ecommerce.order.exception.InvalidOrderStatusException;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;

    @Override
    @Transactional
    @CircuitBreaker(name = "productService")
    public OrderResponse createOrder(UUID userId, OrderRequest request) {
        log.info("Creating order for user: {}", userId);

        Order order = Order.builder()
                .userId(userId)
                .userEmail(request.getUserEmail() != null ? request.getUserEmail() : userId.toString())
                .status(OrderStatus.PENDING)
                .currency(request.getCurrency())
                .shippingAddress(request.getShippingAddress())
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItemDto itemDto : request.getItems()) {
            // Fetch product details
            ProductDto product = productClient.getProduct(itemDto.getProductId());
            
            // Reserve stock
            try {
                inventoryClient.reserveStock(new ReserveStockRequest(product.getId(), itemDto.getQuantity()));
            } catch (Exception e) {
                log.error("Failed to reserve stock for product {}: {}", product.getId(), e.getMessage());
                throw new InsufficientStockException("Could not reserve stock for product: " + product.getName());
            }

            BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(itemDto.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(itemDto.getQuantity())
                    .unitPrice(product.getPrice())
                    .totalPrice(itemTotal)
                    .build();

            order.addItem(orderItem);
        }

        order.setTotalAmount(totalAmount);
        Order savedOrder = orderRepository.save(order);

        return mapToResponse(savedOrder);
    }

    @Override
    public OrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        return mapToResponse(order);
    }

    @Override
    public List<OrderResponse> getUserOrders(UUID userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updatePaymentStatus(UUID orderId, String status) {
        log.info("Updating payment status for order {} to {}", orderId, status);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if ("COMPLETED".equalsIgnoreCase(status)) {
            order.setStatus(OrderStatus.CONFIRMED);
        } else if ("FAILED".equalsIgnoreCase(status)) {
            order.setStatus(OrderStatus.CANCELLED);
            releaseOrderStock(order);
        }
        orderRepository.save(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        log.info("Cancelling order {}", orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.SHIPPED) {
            throw new InvalidOrderStatusException("Cannot cancel order with status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        releaseOrderStock(order);
        
        return mapToResponse(orderRepository.save(order));
    }

    @Override
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus status) {
        log.info("Updating order {} status to {}", orderId, status);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        
        order.setStatus(status);
        return mapToResponse(orderRepository.save(order));
    }

    private void releaseOrderStock(Order order) {
        for (OrderItem item : order.getItems()) {
            try {
                inventoryClient.releaseStock(new ReserveStockRequest(item.getProductId(), item.getQuantity()));
            } catch (Exception e) {
                log.error("Failed to release stock for product {}: {}", item.getProductId(), e.getMessage());
                // In a robust system, we would queue this for retry
            }
        }
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .userEmail(order.getUserEmail())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .shippingAddress(order.getShippingAddress())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
