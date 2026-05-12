package com.ecommerce.product_service.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record ValidatePromoCodeRequest(
        @NotBlank(message = "Code is required")
        String code,
        BigDecimal orderAmount
) {}
