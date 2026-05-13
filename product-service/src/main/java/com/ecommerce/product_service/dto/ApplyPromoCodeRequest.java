package com.ecommerce.product_service.dto;

import java.util.UUID;

public record ApplyPromoCodeRequest(
        String code,
        UUID userId,
        String userEmail
) {}
