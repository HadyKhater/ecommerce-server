package com.ecommerce.order_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for Payment Service using Eureka service discovery.
 * Uses lb://payment-service to load balance through Eureka registry.
 */
@FeignClient(name = "payment-service")
public interface PaymentClient {

    @PostMapping("/api/payments/process")
    PaymentResponse processPayment(@RequestBody ProcessPaymentRequest request);

    // DTO Classes
    record ProcessPaymentRequest(
        String orderId,
        java.math.BigDecimal amount,
        String idempotencyKey
    ) {}

    record PaymentResponse(
        String transactionId,
        String status,
        java.math.BigDecimal amount
    ) {}
}

