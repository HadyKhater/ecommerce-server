package com.ecommerce.product_service.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ValidatePromoCodeRequest(
        @NotBlank(message = "Code is required")
        String code,
        BigDecimal orderAmount,
        UUID userId,
        String userEmail,
        List<UUID> cartCategoryIds
) {}
