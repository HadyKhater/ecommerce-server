package com.ecommerce.product_service.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ApplyPromoCodeRequest(
        String code,
        UUID userId,
        String userEmail,
        BigDecimal orderAmount
) {}
