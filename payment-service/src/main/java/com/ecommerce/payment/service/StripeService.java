package com.ecommerce.payment.service;

import com.ecommerce.payment.dto.StripeCheckoutRequest;
import com.stripe.model.checkout.Session;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface StripeService {

    /**
     * Create a Stripe checkout session for payment
     *
     * @param orderId   The order ID
     * @param userId    The user ID
     * @param amount    The payment amount
     * @param currency  The currency code (e.g., "usd", "eur")
     * @param items     List of line items for the checkout session
     * @param discountCode Optional discount/coupon code to apply
     * @param discountType Optional discount type: "PERCENTAGE" or "FIXED_AMOUNT"
     * @param discountValue Optional discount value (percentage like 20, or fixed amount in cents)
     * @return The checkout session ID
     */
    String createCheckoutSession(
            UUID orderId,
            UUID userId,
            BigDecimal amount,
            String currency,
            List<StripeCheckoutRequest.LineItem> items,
            String discountCode,
            String discountType,
            BigDecimal discountValue
    );

    /**
     * Retrieve a checkout session by ID
     *
     * @param sessionId The Stripe session ID
     * @return The checkout session
     */
    Session retrieveCheckoutSession(String sessionId);

    /**
     * Verify and process a webhook event from Stripe
     *
     * @param payload    The raw webhook payload
     * @param sigHeader  The Stripe-Signature header
     * @return The event type if successfully verified
     */
    String processWebhook(String payload, String sigHeader);

    /**
     * Refund a payment by Stripe payment intent ID
     *
     * @param paymentIntentId The Stripe payment intent ID
     * @return The refund ID
     */
    String refundPayment(String paymentIntentId);
}
