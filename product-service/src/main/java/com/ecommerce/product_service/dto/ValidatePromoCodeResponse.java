package com.ecommerce.product_service.dto;

import java.math.BigDecimal;

public record ValidatePromoCodeResponse(
        boolean valid,
        String message,
        BigDecimal discountAmount,
        PromotionResponse promotion
) {}
