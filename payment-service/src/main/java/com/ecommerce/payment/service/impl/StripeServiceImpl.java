package com.ecommerce.payment.service.impl;

import com.ecommerce.payment.client.OrderServiceClient;
import com.ecommerce.payment.config.StripeProperties;
import com.ecommerce.payment.dto.StripeCheckoutRequest;
import com.ecommerce.payment.exception.PaymentException;
import com.ecommerce.payment.service.StripeCouponService;
import com.ecommerce.payment.service.StripeService;
import com.google.gson.Gson;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeServiceImpl implements StripeService {

    private final StripeProperties stripeProperties;
    private final StripeCouponService stripeCouponService;
    private final OrderServiceClient orderServiceClient;
    private final Gson gson = new Gson();

    @PostConstruct
    public void init() {
        // Initialize Stripe with the API key
        Stripe.apiKey = stripeProperties.getApiKey();
        log.info("Stripe service initialized");
    }

    @Override
    public String createCheckoutSession(
            UUID orderId,
            UUID userId,
            BigDecimal amount,
            String currency,
            List<StripeCheckoutRequest.LineItem> items,
            String discountCode,
            String discountType,
            BigDecimal discountValue
    ) {
        try {
            log.info("Creating Stripe checkout session for order: {} by user: {}", orderId, userId);

            SessionCreateParams.Builder sessionBuilder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(stripeProperties.getSuccessUrl())
                    .setCancelUrl(stripeProperties.getCancelUrl())
                    .putMetadata("orderId", orderId.toString())
                    .putMetadata("userId", userId.toString());

            // Add line items (products only - no negative discount items)
            for (StripeCheckoutRequest.LineItem item : items) {
                // Skip any negative amount items (old discount approach)
                if (item.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                    log.warn("Skipping negative amount line item: {} with amount: {}", 
                             item.getName(), item.getAmount());
                    continue;
                }

                // Build product data, only including description if it's not blank
                SessionCreateParams.LineItem.PriceData.ProductData.Builder productDataBuilder =
                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                .setName(item.getName());

                if (item.getDescription() != null && !item.getDescription().isBlank()) {
                    productDataBuilder.setDescription(item.getDescription());
                }

                SessionCreateParams.LineItem.PriceData priceData = SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(item.getCurrency().toLowerCase())
                        .setUnitAmount(item.getAmount().longValue()) // Amount in smallest currency unit (cents)
                        .setProductData(productDataBuilder.build())
                        .build();

                sessionBuilder.addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPriceData(priceData)
                                .setQuantity(item.getQuantity())
                                .build()
                );
            }

            // Apply discount using Stripe coupon if discount code and type/value are provided
            if (discountCode != null && !discountCode.isBlank() && 
                discountType != null && !discountType.isBlank() &&
                discountValue != null && discountValue.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    log.info("Creating/applying Stripe coupon for discount code: {} with type: {} and value: {}", 
                             discountCode, discountType, discountValue);
                    
                    // Create or retrieve the Stripe coupon
                    String couponId = stripeCouponService.createCoupon(
                            discountCode, 
                            discountType, 
                            discountValue,
                            currency
                    );
                    
                    if (couponId != null) {
                        // Apply the coupon to the checkout session
                        SessionCreateParams.Discount discount = SessionCreateParams.Discount.builder()
                                .setCoupon(couponId)
                                .build();
                        sessionBuilder.addDiscount(discount);
                        sessionBuilder.putMetadata("discountCode", discountCode);
                        sessionBuilder.putMetadata("discountType", discountType);
                        sessionBuilder.putMetadata("discountValue", discountValue.toString());
                        log.info("Discount coupon {} applied to checkout session", couponId);
                    } else {
                        log.warn("Failed to create coupon for discount code: {}", discountCode);
                    }
                } catch (Exception e) {
                    log.error("Failed to apply discount code: {}", discountCode, e);
                    // Continue without discount rather than failing the entire session
                }
            }

            Session session = Session.create(sessionBuilder.build());
            log.info("Stripe checkout session created: {}", session.getId());
            return session.getId();

        } catch (Exception e) {
            log.error("Failed to create Stripe checkout session for order: {}", orderId, e);
            throw new PaymentException("Failed to create payment session: " + e.getMessage());
        }
    }

    @Override
    public Session retrieveCheckoutSession(String sessionId) {
        try {
            log.info("Retrieving Stripe checkout session: {}", sessionId);
            SessionRetrieveParams params = SessionRetrieveParams.builder().build();
            return Session.retrieve(sessionId, params, null);
        } catch (Exception e) {
            log.error("Failed to retrieve Stripe checkout session: {}", sessionId, e);
            throw new PaymentException("Failed to retrieve payment session: " + e.getMessage());
        }
    }

    @Override
    public String processWebhook(String payload, String sigHeader) {
        try {
            log.info("Processing Stripe webhook");

            if (stripeProperties.getWebhookSecret() == null || stripeProperties.getWebhookSecret().isBlank()) {
                log.warn("Stripe webhook secret is not configured. Webhook verification skipped.");
                // Parse the event without verification (not recommended for production)
                Event event = gson.fromJson(payload, Event.class);
                return handleEvent(event);
            }

            Event event = Webhook.constructEvent(
                    payload,
                    sigHeader,
                    stripeProperties.getWebhookSecret()
            );

            return handleEvent(event);

        } catch (SignatureVerificationException e) {
            log.error("Webhook signature verification failed", e);
            throw new PaymentException("Webhook signature verification failed");
        } catch (Exception e) {
            log.error("Failed to process Stripe webhook", e);
            throw new PaymentException("Failed to process webhook: " + e.getMessage());
        }
    }

    private String handleEvent(Event event) {
        String eventType = event.getType();
        log.info("Processing Stripe event: {}", eventType);

        switch (eventType) {
            case "checkout.session.completed":
                handleCheckoutSessionCompleted(event);
                break;
            case "payment_intent.succeeded":
                handlePaymentIntentSucceeded(event);
                break;
            case "payment_intent.payment_failed":
                handlePaymentIntentFailed(event);
                break;
            default:
                log.info("Unhandled event type: {}", eventType);
        }

        return eventType;
    }

    private void handleCheckoutSessionCompleted(Event event) {
        String orderId = null;

        // Method 1: Try standard SDK deserialization
        try {
            if (event.getDataObjectDeserializer().getObject().isPresent()) {
                Session session = (Session) event.getDataObjectDeserializer().getObject().get();
                if (session.getMetadata() != null) {
                    orderId = session.getMetadata().get("orderId");
                    log.info("Got orderId from standard deserialization: {}", orderId);
                }
            }
        } catch (Exception e) {
            log.warn("Standard deserialization failed: {}", e.getMessage());
        }

        // Method 2: Parse raw JSON manually if Method 1 failed
        if (orderId == null) {
            try {
                String rawJson = event.getDataObjectDeserializer().getRawJson();
                log.info("Falling back to raw JSON parsing, length: {}", rawJson != null ? rawJson.length() : 0);
                if (rawJson != null && !rawJson.isEmpty()) {
                    com.google.gson.JsonObject sessionJson = new com.google.gson.Gson().fromJson(rawJson, com.google.gson.JsonObject.class);
                    if (sessionJson != null && sessionJson.has("metadata")) {
                        com.google.gson.JsonObject metadata = sessionJson.getAsJsonObject("metadata");
                        if (metadata != null && metadata.has("orderId")) {
                            orderId = metadata.get("orderId").getAsString();
                            log.info("Got orderId from raw JSON: {}", orderId);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Raw JSON parsing failed: {}", e.getMessage());
            }
        }

        // Method 3: Parse from full event JSON as last resort
        if (orderId == null) {
            try {
                String eventJson = event.toJson();
                log.info("Falling back to full event JSON parsing");
                com.google.gson.JsonObject eventObject = new com.google.gson.Gson().fromJson(eventJson, com.google.gson.JsonObject.class);
                com.google.gson.JsonObject data = eventObject.getAsJsonObject("data");
                com.google.gson.JsonObject sessionObj = data.getAsJsonObject("object");
                com.google.gson.JsonObject metadata = sessionObj.getAsJsonObject("metadata");
                if (metadata != null && metadata.has("orderId")) {
                    orderId = metadata.get("orderId").getAsString();
                    log.info("Got orderId from full event JSON: {}", orderId);
                }
            } catch (Exception e) {
                log.error("Full event JSON parsing failed: {}", e.getMessage());
            }
        }

        if (orderId == null) {
            log.error("Could not extract orderId from checkout.session.completed using any method");
            return;
        }

        log.info("Checkout session completed for order: {}", orderId);
        try {
            orderServiceClient.confirmOrder(UUID.fromString(orderId));
            log.info("Successfully confirmed order {}", orderId);
        } catch (Exception e) {
            log.error("Failed to confirm order {}: {} - {}", orderId, e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    private void handlePaymentIntentSucceeded(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
        if (paymentIntent == null) {
            log.warn("Received payment_intent.succeeded event with null payment intent");
            return;
        }

        log.info("Payment succeeded for payment intent: {}", paymentIntent.getId());
    }

    private void handlePaymentIntentFailed(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
        if (paymentIntent == null) {
            log.warn("Received payment_intent.payment_failed event with null payment intent");
            return;
        }

        log.error("Payment failed for payment intent: {}", paymentIntent.getId());
    }

    @Override
    public String refundPayment(String paymentIntentId) {
        try {
            log.info("Processing refund for payment intent: {}", paymentIntentId);

            Map<String, Object> refundParams = new HashMap<>();
            refundParams.put("payment_intent", paymentIntentId);

            Refund refund = Refund.create(refundParams);
            log.info("Refund processed successfully: {}", refund.getId());
            return refund.getId();

        } catch (Exception e) {
            log.error("Failed to process refund for payment intent: {}", paymentIntentId, e);
            throw new PaymentException("Failed to process refund: " + e.getMessage());
        }
    }
}