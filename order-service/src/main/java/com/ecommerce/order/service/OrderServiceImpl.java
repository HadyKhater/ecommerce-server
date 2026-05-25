package com.ecommerce.order.service;

import com.ecommerce.order.client.InventoryClient;
import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.client.NotificationClient;
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
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

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
    private final RestTemplate restTemplate;
    private final NotificationClient notificationClient;

    @Value("${product.service.url}")
    private String productServiceUrl;

    @Value("${gateway.internal-secret}")
    private String internalSecret;

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

        BigDecimal discountAmount = request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO;
        if (discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            log.error("Invalid discount amount: {}", discountAmount);
            throw new IllegalArgumentException("Discount amount cannot be negative");
        }
        
        BigDecimal finalAmount = totalAmount.subtract(discountAmount);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }

        order.setTotalAmount(finalAmount);
        Order savedOrder = orderRepository.save(order);

        // Apply promotion if exists
        if (request.getPromoCode() != null && !request.getPromoCode().isEmpty()) {
            applyPromotion(request.getPromoCode(), userId, request.getUserEmail());
        }

        return mapToResponse(savedOrder);
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "productService")
    public OrderResponse createOrderReservation(UUID userId, OrderRequest request) {
        log.info("Creating order reservation for user: {} (two-phase commit)", userId);

        Order order = Order.builder()
                .userId(userId)
                .userEmail(request.getUserEmail() != null ? request.getUserEmail() : userId.toString())
                .status(OrderStatus.RESERVED)  // RESERVED status - not yet confirmed
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

        // Only accept discountAmount if promoCode was provided (already validated in checkout)
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (request.getPromoCode() != null && !request.getPromoCode().isEmpty()) {
            log.info("Applying discount for promo code: {}", request.getPromoCode());
            discountAmount = request.getDiscountAmount() != null 
                ? request.getDiscountAmount() 
                : BigDecimal.ZERO;
        }
        
        if (discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            log.error("Invalid discount amount: {}", discountAmount);
            throw new IllegalArgumentException("Discount amount cannot be negative");
        }
        
        BigDecimal finalAmount = totalAmount.subtract(discountAmount);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }

        order.setTotalAmount(finalAmount);

        if (request.getPromoCode() != null && !request.getPromoCode().isBlank()) {
            order.setPromoCode(request.getPromoCode());
        }

        Order savedOrder = orderRepository.save(order);

        log.info("Order reservation created: {} with status RESERVED, awaiting payment confirmation", savedOrder.getId());
        
        // Send order status notification (wrapped in try-catch internally)
        sendNotificationSafe(savedOrder.getUserId(), "Order Awaiting Payment", 
                "Your order has been placed and is awaiting payment", "ORDER");

        return mapToResponse(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponse confirmOrder(UUID orderId) {
        log.info("Confirming reserved order: {}", orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.RESERVED) {
            throw new InvalidOrderStatusException("Only RESERVED orders can be confirmed. Current status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CONFIRMED);
        Order savedOrder = orderRepository.save(order);
        
        if (order.getPromoCode() != null && !order.getPromoCode().isBlank()) {
            applyPromotion(order.getPromoCode(), order.getUserId(), order.getUserEmail());
        }
        
        // Send order status notification
        sendNotificationSafe(savedOrder.getUserId(), "Order Confirmed", 
                "Your order has been confirmed and is being processed", "ORDER");
        
        return mapToResponse(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponse cancelReservation(UUID orderId) {
        log.info("Cancelling order reservation: {}", orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.RESERVED) {
            throw new InvalidOrderStatusException("Only RESERVED orders can be cancelled via this endpoint. Current status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        releaseOrderStock(order);
        Order savedOrder = orderRepository.save(order);
        
        // Send order status notification
        sendNotificationSafe(savedOrder.getUserId(), "Order Cancelled", 
                "Your order has been cancelled", "ORDER");
        
        return mapToResponse(savedOrder);
    }

    private void applyPromotion(String code, UUID userId, String userEmail) {
        try {
            log.info("Calling product-service to apply promo code: {}", code);
            String url = productServiceUrl + "/promotions/apply";

            ApplyPromoCodeRequest applyRequest = new ApplyPromoCodeRequest(code, userId, userEmail, null);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Gateway-Secret", internalSecret);

            HttpEntity<ApplyPromoCodeRequest> entity = new HttpEntity<>(applyRequest, headers);

            restTemplate.postForEntity(url, entity, Void.class);
            log.info("Successfully called product-service to apply promo code: {}", code);
        } catch (Exception e) {
            log.error("Failed to apply promo code {} for user {}: {}", code, userId, e.getMessage());
            // Catch and LOG any errors silently (don't fail the order)
        }
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
                .filter(order -> order.getStatus() != OrderStatus.RESERVED && order.getStatus() != OrderStatus.EXPIRED)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updatePaymentStatus(UUID orderId, String status) {
        log.info("Updating payment status for order {} to {}", orderId, status);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        OrderStatus oldStatus = order.getStatus();
        if ("COMPLETED".equalsIgnoreCase(status)) {
            order.setStatus(OrderStatus.CONFIRMED);
        } else if ("FAILED".equalsIgnoreCase(status)) {
            order.setStatus(OrderStatus.CANCELLED);
            releaseOrderStock(order);
        }
        Order savedOrder = orderRepository.save(order);

        // Notify if status changed
        if (savedOrder.getStatus() != oldStatus) {
            if (savedOrder.getStatus() == OrderStatus.CONFIRMED) {
                sendNotificationSafe(savedOrder.getUserId(), "Order Confirmed", 
                        "Your order has been confirmed and is being processed", "ORDER");
            } else if (savedOrder.getStatus() == OrderStatus.CANCELLED) {
                sendNotificationSafe(savedOrder.getUserId(), "Order Cancelled", 
                        "Your order has been cancelled", "ORDER");
            }
        }
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
        Order savedOrder = orderRepository.save(order);
        
        // Send order status notification
        sendNotificationSafe(savedOrder.getUserId(), "Order Cancelled", 
                "Your order has been cancelled", "ORDER");
        
        return mapToResponse(savedOrder);
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
        
        // Validate status transitions for RESERVED orders
        if (order.getStatus() == OrderStatus.RESERVED) {
            if (status != OrderStatus.CANCELLED) {
                throw new InvalidOrderStatusException("Reserved orders can only be cancelled manually. Complete payment to confirm.");
            }
        }
        
        // Validate status transitions for EXPIRED orders
        if (order.getStatus() == OrderStatus.EXPIRED) {
            throw new InvalidOrderStatusException("Expired orders cannot be modified.");
        }
        
        OrderStatus oldStatus = order.getStatus();
        order.setStatus(status);
        Order savedOrder = orderRepository.save(order);
        
        // Notify if status changed
        if (savedOrder.getStatus() != oldStatus) {
            if (savedOrder.getStatus() == OrderStatus.RESERVED) {
                sendNotificationSafe(savedOrder.getUserId(), "Order Awaiting Payment", 
                        "Your order has been placed and is awaiting payment", "ORDER");
            } else if (savedOrder.getStatus() == OrderStatus.CONFIRMED) {
                sendNotificationSafe(savedOrder.getUserId(), "Order Confirmed", 
                        "Your order has been confirmed and is being processed", "ORDER");
            } else if (savedOrder.getStatus() == OrderStatus.CANCELLED) {
                sendNotificationSafe(savedOrder.getUserId(), "Order Cancelled", 
                        "Your order has been cancelled", "ORDER");
            } else if (savedOrder.getStatus() == OrderStatus.EXPIRED) {
                sendNotificationSafe(savedOrder.getUserId(), "Order Reservation Expired", 
                        "Your order reservation has expired", "ORDER");
            }
        }
        
        return mapToResponse(savedOrder);
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

    private void sendNotificationSafe(UUID userId, String title, String message, String type) {
        try {
            log.info("Sending order status notification to user {}: {} - {}", userId, title, message);
            notificationClient.sendNotification(new SendNotificationRequest(userId, title, message, type));
        } catch (Exception e) {
            log.error("Failed to send order status notification to user {} (this is non-critical): {}", 
                    userId, e.getMessage());
        }
    }
}
