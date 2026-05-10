package com.ecommerce.payment.service.impl;

import com.ecommerce.payment.client.OrderServiceClient;
import com.ecommerce.payment.dto.PaymentRequest;
import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.exception.PaymentException;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.service.PaymentService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderServiceClient orderServiceClient;

    @Override
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, UUID userId) {
        log.info("Processing payment for order: {} by user: {}", request.getOrderId(), userId);

        // Idempotency check: if a payment for this order already exists
        Optional<Payment> existingPayment = paymentRepository.findByOrderId(request.getOrderId());
        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                log.info("Payment for order {} was already processed successfully", request.getOrderId());
                return mapToResponse(payment);
            } else if (payment.getStatus() == PaymentStatus.PENDING) {
                log.info("Payment for order {} is already in progress", request.getOrderId());
                return mapToResponse(payment);
            }
            // If failed, we could retry creating a new payment or update the existing one.
            // For simplicity, let's just let it proceed and update later, or we can just reject if we strictly do one payment entity per order.
            // Let's create a new payment attempt if the previous failed, or just update the existing. Let's update the existing.
            log.info("Retrying failed payment for order {}", request.getOrderId());
        }

        Payment payment = existingPayment.orElseGet(() -> Payment.builder()
                .orderId(request.getOrderId())
                .userId(userId)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .paymentMethod(request.getPaymentMethod())
                .status(PaymentStatus.PENDING)
                .build());

        payment = paymentRepository.save(payment);

        try {
            // Mock payment provider call with retry
            String transactionId = mockPaymentProviderCall(payment.getId().toString());
            payment.setTransactionId(transactionId);
            payment.setStatus(PaymentStatus.SUCCESS);
            payment = paymentRepository.save(payment);
            
            // Notify Order Service
            notifyOrderService(payment.getOrderId(), payment.getStatus().name());

        } catch (Exception e) {
            log.error("Payment failed for order {}", request.getOrderId(), e);
            payment.setStatus(PaymentStatus.FAILED);
            payment = paymentRepository.save(payment);
            notifyOrderService(payment.getOrderId(), payment.getStatus().name());
            throw new PaymentException("Payment processing failed: " + e.getMessage());
        }

        return mapToResponse(payment);
    }

    @Retry(name = "paymentProviderRetry", fallbackMethod = "mockPaymentProviderFallback")
    protected String mockPaymentProviderCall(String paymentId) {
        log.info("Calling mock payment provider for payment ID: {}", paymentId);
        // Simulate random failure
        if (Math.random() > 0.8) {
            throw new RuntimeException("Mock payment provider timeout/error");
        }
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public String mockPaymentProviderFallback(String paymentId, Exception e) {
        log.error("All retries failed for payment ID: {}", paymentId);
        throw new PaymentException("Payment gateway is unavailable");
    }

    @CircuitBreaker(name = "orderService", fallbackMethod = "notifyOrderServiceFallback")
    protected void notifyOrderService(UUID orderId, String status) {
        log.info("Notifying Order Service to update status of order {} to {}", orderId, status);
        orderServiceClient.updateOrderPaymentStatus(orderId, status);
    }

    public void notifyOrderServiceFallback(UUID orderId, String status, Exception e) {
        log.error("Failed to notify Order Service for order {} with status {}", orderId, status, e);
        // Could send to a dead letter queue or save for later retry
    }

    @Override
    public PaymentResponse getPaymentById(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException("Payment not found"));
        return mapToResponse(payment);
    }

    @Override
    public PaymentResponse getPaymentByOrderId(UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentException("Payment not found for order"));
        return mapToResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse refundPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException("Payment not found"));

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new PaymentException("Only successful payments can be refunded");
        }

        // Mock refund process
        log.info("Processing refund for payment {}", paymentId);
        payment.setStatus(PaymentStatus.REFUNDED);
        payment = paymentRepository.save(payment);

        // Notify Order Service
        notifyOrderService(payment.getOrderId(), payment.getStatus().name());

        return mapToResponse(payment);
    }

    @Override
    public List<PaymentResponse> getPaymentHistory(UUID userId) {
        return paymentRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .transactionId(payment.getTransactionId())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
