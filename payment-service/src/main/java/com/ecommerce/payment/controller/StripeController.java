package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.StripeCheckoutRequest;
import com.ecommerce.payment.dto.StripeCheckoutResponse;
import com.ecommerce.payment.service.StripeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/stripe")
@RequiredArgsConstructor
public class StripeController {

    private final StripeService stripeService;

    /**
     * Create a Stripe checkout session
     */
    @PostMapping("/checkout")
    public ResponseEntity<StripeCheckoutResponse> createCheckoutSession(
            @Valid @RequestBody StripeCheckoutRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader
    ) {
        log.info("Creating Stripe checkout session for order: {}", request.getOrderId());

        UUID userId = userIdHeader != null ? UUID.fromString(userIdHeader) : null;

        String sessionId = stripeService.createCheckoutSession(
                request.getOrderId(),
                userId,
                request.getAmount(),
                request.getCurrency(),
                request.getItems(),
                request.getDiscountCode(),
                request.getDiscountType(),
                request.getDiscountValue()
        );

        // Retrieve the session to get the URL
        var session = stripeService.retrieveCheckoutSession(sessionId);

        StripeCheckoutResponse response = StripeCheckoutResponse.builder()
                .sessionId(sessionId)
                .orderId(request.getOrderId())
                .url(session.getUrl())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve a Stripe checkout session
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<StripeCheckoutResponse> retrieveCheckoutSession(
            @PathVariable String sessionId
    ) {
        log.info("Retrieving Stripe checkout session: {}", sessionId);

        var session = stripeService.retrieveCheckoutSession(sessionId);

        StripeCheckoutResponse response = StripeCheckoutResponse.builder()
                .sessionId(session.getId())
                .status(session.getStatus())
                .paymentStatus(session.getPaymentStatus())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Stripe webhook endpoint
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        log.info("Received Stripe webhook");

        try {
            String eventType = stripeService.processWebhook(payload, sigHeader);
            return ResponseEntity.ok(eventType);
        } catch (Exception e) {
            log.error("Failed to process Stripe webhook", e);
            return ResponseEntity.badRequest().body("Webhook processing failed: " + e.getMessage());
        }
    }
}