package com.ecommerce.payment.service;

import com.ecommerce.payment.dto.PaymentRequest;
import com.ecommerce.payment.dto.PaymentResponse;

import java.util.List;
import java.util.UUID;

public interface PaymentService {
    PaymentResponse processPayment(PaymentRequest request, UUID userId);
    PaymentResponse getPaymentById(UUID paymentId);
    PaymentResponse getPaymentByOrderId(UUID orderId);
    PaymentResponse refundPayment(UUID paymentId);
    List<PaymentResponse> getPaymentHistory(UUID userId);
}
