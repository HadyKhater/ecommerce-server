package com.ecommerce.order.dto;

import java.util.UUID;

public record ApplyPromoCodeRequest(
        String code,
        UUID userId,
        String userEmail
) {}
