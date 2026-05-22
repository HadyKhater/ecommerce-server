package com.ecommerce.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripeCheckoutResponse {

    private String sessionId;
    private UUID orderId;
    private String status;
    private String paymentStatus;
    private String url;
}