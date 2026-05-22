package com.ecommerce.payment.service;

import java.math.BigDecimal;

/**
 * Service interface for managing Stripe coupons
 */
public interface StripeCouponService {

    /**
     * Create a Stripe coupon for a discount
     *
     * @param code The coupon code
     * @param discountType The discount type: "PERCENTAGE" or "FIXED_AMOUNT"
     * @param discountValue The discount value (percentage value like 20, or fixed amount in cents)
     * @param currency The currency code (required for FIXED_AMOUNT)
     * @return The Stripe coupon ID
     */
    String createCoupon(String code, String discountType, BigDecimal discountValue, String currency);

    /**
     * Retrieve an existing Stripe coupon by code
     *
     * @param code The coupon code
     * @return The Stripe coupon ID if found, null otherwise
     */
    String getCouponIdByCode(String code);
}