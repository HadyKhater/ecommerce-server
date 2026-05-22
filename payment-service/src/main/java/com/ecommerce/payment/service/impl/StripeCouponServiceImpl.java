package com.ecommerce.payment.service.impl;

import com.ecommerce.payment.service.StripeCouponService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Coupon;
import com.stripe.param.CouponCreateParams;
import com.stripe.param.CouponListParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeCouponServiceImpl implements StripeCouponService {

    private final com.ecommerce.payment.config.StripeProperties stripeProperties;

    // Cache for coupon IDs to avoid creating duplicates
    private final Map<String, String> couponCodeToIdCache = new HashMap<>();

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeProperties.getApiKey();
        log.info("Stripe coupon service initialized");
    }

    @Override
    public String createCoupon(String code, String discountType, BigDecimal discountValue, String currency) {
        try {
            // Check if coupon already exists in cache
            String existingCouponId = couponCodeToIdCache.get(code);
            if (existingCouponId != null) {
                log.info("Using cached coupon for code: {}", code);
                return existingCouponId;
            }

            // Check if coupon already exists in Stripe
            String existingStripeCouponId = getCouponIdByCode(code);
            if (existingStripeCouponId != null) {
                log.info("Found existing Stripe coupon for code: {}", code);
                couponCodeToIdCache.put(code, existingStripeCouponId);
                return existingStripeCouponId;
            }

            log.info("Creating Stripe coupon - Code: {}, Type: {}, Value: {}, Currency: {}", 
                     code, discountType, discountValue, currency);

            // Create coupon parameters based on discount type
            Map<String, Object> params = new HashMap<>();
            params.put("name", "Discount: " + code);
            params.put("id", code); // Use the code as the coupon ID
            params.put("duration", "once");

            if ("PERCENTAGE".equalsIgnoreCase(discountType)) {
                // For percentage discounts, use percent_off
                int percentOff = discountValue.intValue();
                log.info("Creating percentage coupon: {}% off", percentOff);
                params.put("percent_off", percentOff);
            } else if ("FIXED_AMOUNT".equalsIgnoreCase(discountType)) {
                // For fixed amount discounts, use amount_off in cents
                long amountInCents = discountValue.longValue();
                log.info("Creating fixed amount coupon: {} cents off", amountInCents);
                params.put("amount_off", amountInCents);
                params.put("currency", currency.toLowerCase());
            } else {
                throw new IllegalArgumentException("Invalid discount type: " + discountType + ". Must be PERCENTAGE or FIXED_AMOUNT");
            }
            
            Coupon coupon = Coupon.create(params);

            // Cache the coupon ID
            couponCodeToIdCache.put(code, coupon.getId());
            
            log.info("Stripe coupon created successfully: {} with ID: {}", code, coupon.getId());
            return coupon.getId();

        } catch (StripeException e) {
            // If coupon already exists error, try to retrieve it
            if (e.getCode() != null && (e.getCode().equals("resource_missing") || e.getStatusCode() == 404)) {
                log.warn("Coupon creation failed, trying to retrieve existing: {}", e.getMessage());
                return getCouponIdByCode(code);
            }
            log.error("Failed to create Stripe coupon for code: {}", code, e);
            throw new RuntimeException("Failed to create Stripe coupon: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error creating Stripe coupon for code: {}", code, e);
            throw new RuntimeException("Failed to create Stripe coupon: " + e.getMessage(), e);
        }
    }

    @Override
    public String getCouponIdByCode(String code) {
        try {
            // List coupons with the given code
            CouponListParams params = CouponListParams.builder()
                    .setLimit(100L)
                    .build();

            Iterable<Coupon> coupons = Coupon.list(params).autoPagingIterable();
            
            for (Coupon coupon : coupons) {
                // Use coupon ID instead of code since getCode() is not available
                if (coupon.getId() != null && coupon.getId().equals(code)) {
                    // Cache the result
                    couponCodeToIdCache.put(code, coupon.getId());
                    return coupon.getId();
                }
            }
            
            return null;
        } catch (StripeException e) {
            log.error("Failed to retrieve Stripe coupon for code: {}", code, e);
            return null;
        } catch (Exception e) {
            log.error("Unexpected error retrieving Stripe coupon for code: {}", code, e);
            return null;
        }
    }
}