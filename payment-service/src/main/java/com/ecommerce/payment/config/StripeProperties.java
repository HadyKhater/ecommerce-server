package com.ecommerce.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "stripe")
public class StripeProperties {

    /**
     * Stripe Secret API Key (sk_test_... or sk_live_...)
     */
    private String apiKey;

    /**
     * Stripe Publishable Key (pk_test_... or pk_live_...)
     */
    private String publicKey;

    /**
     * Stripe Webhook Signing Secret (whsec_...)
     */
    private String webhookSecret;

    /**
     * URL to redirect after successful payment
     */
    private String successUrl;

    /**
     * URL to redirect after cancelled payment
     */
    private String cancelUrl;
}