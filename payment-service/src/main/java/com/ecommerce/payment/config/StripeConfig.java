package com.ecommerce.payment.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class StripeConfig {

    private final StripeProperties stripeProperties;

    @PostConstruct
    public void init() {
        if (stripeProperties.getApiKey() == null || stripeProperties.getApiKey().isBlank()) {
            log.warn("Stripe API key is not configured. Stripe payments will not work.");
            return;
        }
        // Stripe API key is passed via RequestOptions in each call
        log.info("Stripe SDK configured successfully with key ending in: {}", 
            stripeProperties.getApiKey().substring(stripeProperties.getApiKey().length() - 8));
    }
}
