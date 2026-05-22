package com.ecommerce.order.entity;

public enum OrderStatus {
    PENDING,
    RESERVED,     // Temporary reservation awaiting payment
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED,
    EXPIRED       // Reservation expired without payment
}
